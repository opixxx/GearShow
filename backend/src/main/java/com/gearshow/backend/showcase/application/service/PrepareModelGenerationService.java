package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.PrepareModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 3D 모델 생성 준비 서비스.
 *
 * <p>Worker 가 수신한 메시지에 대해 다음을 수행한다:</p>
 * <ol>
 *   <li>모델 상태 확인 (REQUESTED 아니면 skip — 재전달/중복 메시지 방어)</li>
 *   <li>Tripo startGeneration 호출 (이미지 업로드 + task 생성)</li>
 *   <li>task_id 와 함께 모델을 GENERATING 으로 전환</li>
 * </ol>
 *
 * <p>비즈니스 실패 시 예외를 던지지 않고 모델 상태만 전환한다:</p>
 * <ul>
 *   <li>Tripo Circuit Breaker OPEN → UNAVAILABLE (서비스 장애, 사용자 수동 재시도)</li>
 *   <li>기타 RuntimeException → FAILED (사용자에게 실패 사유 노출)</li>
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
            // 중복 메시지/재전달 시나리오: 이미 GENERATING/COMPLETED/FAILED 상태면 skip
            log.info("REQUESTED 상태가 아니어서 처리를 건너뜁니다 - showcase3dModelId: {}, status: {}",
                    showcase3dModelId, model.getModelStatus());
            return;
        }

        try {
            String taskId = modelGenerationClient.startGeneration(showcase3dModelId, showcaseId);
            Showcase3dModel generating = model.markGenerating(taskId);
            showcase3dModelPort.save(generating);
            log.info("3D 모델 생성 시작 - showcase3dModelId: {}, taskId: {}",
                    showcase3dModelId, taskId);
        } catch (CallNotPermittedException e) {
            // Tripo Circuit Breaker 가 OPEN — 서비스 일시 이용 불가.
            // 모델을 UNAVAILABLE 로 마킹하여 사용자가 수동 재시도할 수 있게 한다.
            log.warn("Tripo Circuit Breaker OPEN - 모델을 UNAVAILABLE 로 전환 - showcase3dModelId: {}",
                    showcase3dModelId);
            Showcase3dModel unavailable = model.markUnavailable("3D 생성 서비스가 일시적으로 이용 불가합니다");
            showcase3dModelPort.save(unavailable);
        } catch (RuntimeException e) {
            // Tripo 호출/저장의 그 외 실패 — FAILED 로 전환하고 정상 반환한다.
            // Kafka 재시도는 멱등성 가드에 막혀 무의미하므로 내부에서 상태만 전환.
            log.error("3D 모델 생성 시작 실패 - showcase3dModelId: {}", showcase3dModelId, e);
            Showcase3dModel failed = model.fail("생성 시작 실패: " + e.getMessage());
            showcase3dModelPort.save(failed);
        }
    }
}
