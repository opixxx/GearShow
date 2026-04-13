package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoApiException;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoNonRetryableException;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoRetryableException;
import com.gearshow.backend.showcase.application.port.in.PrepareModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 3D 모델 생성 준비 서비스.
 *
 * <p>Worker 가 수신한 메시지에 대해 다음을 수행한다:</p>
 * <ol>
 *   <li>모델 상태 확인 (REQUESTED 아니면 skip — 재전달/중복 메시지 방어)</li>
 *   <li><b>PREPARING 상태로 선행 전환</b> — "외부 API 호출 전에 의도를 기록"</li>
 *   <li>Tripo startGeneration 호출 (이미지 업로드 + task 생성)</li>
 *   <li>task_id 와 함께 모델을 GENERATING 으로 전환</li>
 * </ol>
 *
 * <p><b>설계 결정 #1 (PREPARING 선행 전환)</b>: Tripo 호출 전에 상태를 PREPARING 으로
 * 바꿔두어, Recovery 스케줄러와 다른 Worker 가 끼어드는 것을 방지한다.
 * PREPARING 상태에서 generationTaskId 는 반드시 NULL 이므로
 * Tripo 과금이 발생하지 않았음이 구조적으로 보장된다.</p>
 *
 * <p><b>설계 결정 #4 (Tripo 에러 분류)</b>:</p>
 * <ul>
 *   <li>{@link TripoRetryableException} (429, 500) → PREPARING 유지, Recovery 가 자동 재시도</li>
 *   <li>{@link TripoNonRetryableException} (400, 401, 403) → 즉시 FAILED</li>
 *   <li>{@link CallNotPermittedException} → UNAVAILABLE (Circuit Breaker)</li>
 * </ul>
 *
 * <p><b>트랜잭션 범위</b>: 이 메서드는 의도적으로 전체를 트랜잭션으로 감싸지 않는다.
 * Tripo 외부 호출 동안 DB 커넥션을 점유하면 HikariCP 풀이 고갈되기 때문.
 * 각 DB 조작은 Showcase3dModelPort 구현체의 @Transactional 로 개별 커밋된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareModelGenerationService implements PrepareModelGenerationUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelGenerationClient modelGenerationClient;

    @Override
    public void prepare(Long showcase3dModelId, Long showcaseId) {
        Showcase3dModel model = showcase3dModelPort.findById(showcase3dModelId)
                .orElse(null);

        if (model == null) {
            log.warn("3D 모델을 찾을 수 없습니다 - showcase3dModelId: {}", showcase3dModelId);
            return;
        }

        if (model.getModelStatus() != ModelStatus.REQUESTED) {
            // 중복 메시지/재전달 시나리오: 이미 PREPARING/GENERATING/COMPLETED/FAILED 면 skip
            log.info("REQUESTED 상태가 아니어서 처리를 건너뜁니다 - showcase3dModelId: {}, status: {}",
                    showcase3dModelId, model.getModelStatus());
            return;
        }

        // [설계 결정 #1] PREPARING 상태로 선행 전환 — "의도 기록"
        // 이 시점 이후로 Recovery 가 이 모델을 "REQUESTED stuck" 으로 오판하지 않는다.
        Showcase3dModel preparing = model.markPreparing();
        showcase3dModelPort.save(preparing);
        log.info("PREPARING 전환 완료 - showcase3dModelId: {}", showcase3dModelId);

        // Tripo API 호출 (이미지 업로드 + task 생성)
        String taskId;
        try {
            taskId = modelGenerationClient.startGeneration(showcase3dModelId, showcaseId);
        } catch (CallNotPermittedException e) {
            // Tripo Circuit Breaker OPEN — 서비스 일시 이용 불가
            log.warn("Tripo Circuit Breaker OPEN - 모델을 UNAVAILABLE 로 전환 - showcase3dModelId: {}",
                    showcase3dModelId);
            Showcase3dModel unavailable = preparing.markUnavailable("3D 생성 서비스가 일시적으로 이용 불가합니다");
            showcase3dModelPort.save(unavailable);
            return;
        } catch (TripoNonRetryableException e) {
            // [설계 결정 #4] 영구 실패 — 즉시 FAILED (재시도 무의미)
            log.error("Tripo 영구 실패 (Non-retryable) - showcase3dModelId: {}, errorCode: {}",
                    showcase3dModelId, e.getMessage(), e);
            Showcase3dModel failed = preparing.fail(e.getMessage());
            showcase3dModelPort.save(failed);
            if (e.isAlertRequired()) {
                // 크레딧 부족, 인증 실패 등 → 개발자에게 알림 필요
                log.error("ALERT 필요: Tripo 크레딧 부족 또는 인증 실패 - 전체 3D 생성 서비스에 영향 - "
                        + "showcase3dModelId: {}", showcase3dModelId);
            }
            return;
        } catch (TripoRetryableException e) {
            // [설계 결정 #4] 일시적 장애 — PREPARING 유지, Recovery 가 자동 재시도
            log.warn("Tripo 일시적 장애 (Retryable) - PREPARING 유지 - showcase3dModelId: {}, error: {}",
                    showcase3dModelId, e.getMessage());
            // PREPARING + taskId=NULL 상태 유지 → Recovery 가 retryCount 기반으로 재시도
            return;
        } catch (DataAccessException e) {
            // 인프라 일시 장애는 DLT 로 보내 수동 재처리 (비즈니스 FAILED 와 구분)
            log.error("3D 모델 생성 시작 중 DB 장애 - showcase3dModelId: {}", showcase3dModelId, e);
            throw e;
        } catch (RestClientException e) {
            // Tripo API 호출 실패 (HTTP 네트워크/타임아웃 등) — 비즈니스 실패로 간주
            log.error("Tripo API 호출 실패 - showcase3dModelId: {}", showcase3dModelId, e);
            Showcase3dModel failed = preparing.fail("3D 모델 생성을 시작하지 못했습니다");
            showcase3dModelPort.save(failed);
            return;
        }

        // Tripo task 생성 성공 — taskId 와 함께 GENERATING 전환
        persistGenerating(preparing, taskId, showcase3dModelId);
    }

    /**
     * GENERATING 전환을 DB 에 저장한다. 저장 실패 시 orphan task 마킹을 시도한다.
     */
    private void persistGenerating(Showcase3dModel model, String taskId, Long showcase3dModelId) {
        try {
            Showcase3dModel generating = model.markGenerating(taskId);
            showcase3dModelPort.save(generating);
            log.info("3D 모델 생성 시작 - showcase3dModelId: {}, taskId: {}",
                    showcase3dModelId, taskId);
        } catch (DataAccessException e) {
            log.error("CRITICAL: Tripo task 생성 후 DB 저장 실패 - "
                            + "orphan task_id: {}, showcase3dModelId: {} (수동 복구 필요)",
                    taskId, showcase3dModelId, e);
            markOrphanToPreventDuplicateCharge(model, taskId, showcase3dModelId);
        }
    }

    /**
     * 모델을 FAILED 로 마킹하여 recovery 스케줄러의 재발행을 차단한다.
     * 이 작업도 실패하면 로그만 남기고 조용히 종료한다 (예외 전파 시 또 다른 재시도 유발).
     */
    private void markOrphanToPreventDuplicateCharge(Showcase3dModel model, String taskId, Long showcase3dModelId) {
        try {
            Showcase3dModel failed = model.fail(
                    "Tripo task 생성되었으나 DB 저장 실패 (orphan task_id=" + taskId + ")");
            showcase3dModelPort.save(failed);
            log.warn("orphan 마킹 완료 - showcase3dModelId: {}, taskId: {}",
                    showcase3dModelId, taskId);
        } catch (DataAccessException inner) {
            log.error("CRITICAL: orphan 마킹 실패 - 수동 복구 필수 (modelId: {}, taskId: {})",
                    showcase3dModelId, taskId, inner);
        }
    }
}
