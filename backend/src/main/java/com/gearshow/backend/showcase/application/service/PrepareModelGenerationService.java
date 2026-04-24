package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;
import com.gearshow.backend.showcase.application.exception.ModelGenerationRetryableException;
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
 * 3D 모델 생성 준비 서비스 — <b>레거시 (P1-D-α+β 이후 Worker 경로에서 미호출)</b>.
 *
 * @deprecated P1-D-α+β 부터 Worker 는 {@code PrepareWorkflowService} 를 호출한다. 이 구현체는
 *     {@code showcase_3d_model.model_status} 기반 pre-P1 경로로, 더 이상 프로덕션 트래픽을 받지
 *     않는다. P1-F (`showcase_3d_model` 프로세스 컬럼 축소) 에서 전체 제거 예정.
 */
@Deprecated(forRemoval = true)
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("removal")
public class PrepareModelGenerationService implements PrepareModelGenerationUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelGenerationClient modelGenerationClient;

    @Override
    public void prepare(Long showcase3dModelId, Long showcaseId) {
        // [설계 결정 #1] PREPARING 상태로 원자적 선행 전환.
        // WHERE model_status = REQUESTED 조건으로 DB 레벨에서 1 Worker 만 성공하도록 보장한다.
        // check-then-act race (findById → 상태 확인 → save) 를 제거하여
        // 두 Worker 가 동시에 PREPARING 전환에 성공하는 것을 원천 차단한다.
        int updated = showcase3dModelPort.updateStatusIfCurrent(
                showcase3dModelId, ModelStatus.REQUESTED, ModelStatus.PREPARING);

        if (updated == 0) {
            // 다른 Worker 가 이미 PREPARING 으로 전환했거나, 모델이 없거나, 다른 상태
            log.info("REQUESTED→PREPARING 원자적 전환 실패 (이미 처리 중 또는 대상 없음) "
                    + "- showcase3dModelId: {}", showcase3dModelId);
            return;
        }

        log.info("PREPARING 전환 완료 - showcase3dModelId: {}", showcase3dModelId);

        // 원자적 전환 성공 → 최신 도메인 모델을 다시 읽어서 이후 로직에 사용
        Showcase3dModel preparing = showcase3dModelPort.findById(showcase3dModelId)
                .orElse(null);
        if (preparing == null) {
            log.error("PREPARING 전환 후 모델 조회 실패 - showcase3dModelId: {}", showcase3dModelId);
            return;
        }

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
        } catch (ModelGenerationNonRetryableException e) {
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
        } catch (ModelGenerationRetryableException e) {
            // [설계 결정 #4] 일시적 장애 — PREPARING 유지, Recovery 가 자동 재시도.
            // 이 경로에서 예외를 Worker 까지 전파하지 않는 것은 의도적 결정이다:
            // - 예외를 던지면 Worker 의 release() 가 호출되어 Kafka 수준 재시도가 즉시 일어남
            // - 하지만 429/500 은 수초~수분 후 복구되는 일시적 장애이므로 즉시 재시도보다
            //   preparingStuckMinutes(기본 2분) 대기 후 Recovery 가 처리하는 것이 적절함
            // - 이 "의도적 쿨다운" 이 Tripo rate limit 초과를 방지하는 역할도 한다.
            log.warn("일시적 장애 (Retryable) - PREPARING 유지, Recovery 대기 - showcase3dModelId: {}, error: {}",
                    showcase3dModelId, e.getMessage());
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
