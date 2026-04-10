package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.RecoverStuckModelsUseCase;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import com.gearshow.backend.showcase.infrastructure.config.StuckRecoveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 좀비/stuck 상태의 3D 모델 복구 서비스.
 *
 * <p>두 가지 복구 시나리오:</p>
 * <ol>
 *   <li><b>REQUESTED stuck</b>: Outbox 의 안전망이 정상 동작하면 보통 수 초 내에 Worker 가
 *       집어간다. 5분 넘게 REQUESTED 인 모델은 예외 케이스 — Outbox 재등록을 통해 다시 발행한다.
 *       Publisher 의 save 는 새 messageId 를 생성하므로 멱등성 가드에 막히지 않는다.</li>
 *   <li><b>GENERATING + task_id 없음 (좀비)</b>: Worker 가 tryAcquire 후 Tripo 호출 도중 크래시한
 *       edge case. 상태는 GENERATING 이지만 task_id 가 없어 폴링 스케줄러도 집어갈 수 없다.
 *       5분 이상 지속되면 FAILED 로 전환하여 사용자가 인지하고 수동 재요청할 수 있게 한다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecoverStuckModelsService implements RecoverStuckModelsUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelGenerationEventPublisher modelGenerationEventPublisher;
    private final StuckRecoveryProperties properties;

    @Override
    public int recoverOnce() {
        int recovered = 0;
        recovered += recoverStuckRequested();
        recovered += failZombieGenerating();
        return recovered;
    }

    /**
     * REQUESTED 상태에서 오래 머물러 있는 모델을 Outbox 에 재등록한다.
     */
    private int recoverStuckRequested() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.requestedStuckMinutes()));

        List<Showcase3dModel> stuck = showcase3dModelPort
                .findStaleByStatus(ModelStatus.REQUESTED, threshold, properties.batchSize());

        for (Showcase3dModel model : stuck) {
            try {
                modelGenerationEventPublisher.publishRequested(model.getId(), model.getShowcaseId());
                log.warn("REQUESTED stuck 복구 - Outbox 재등록 - showcase3dModelId: {}",
                        model.getId());
            } catch (RuntimeException e) {
                log.error("REQUESTED stuck 복구 실패 - showcase3dModelId: {}", model.getId(), e);
            }
        }
        return stuck.size();
    }

    /**
     * GENERATING 상태이지만 task_id 가 없는 좀비 모델을 FAILED 로 전환한다.
     */
    private int failZombieGenerating() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.generatingWithoutTaskIdStuckMinutes()));

        List<Showcase3dModel> candidates = showcase3dModelPort
                .findStaleByStatus(ModelStatus.GENERATING, threshold, properties.batchSize());

        int failed = 0;
        for (Showcase3dModel model : candidates) {
            if (model.getGenerationTaskId() != null && !model.getGenerationTaskId().isBlank()) {
                // task_id 가 있으면 폴링 스케줄러가 처리 중이므로 skip (정상)
                continue;
            }
            try {
                Showcase3dModel failedModel = model.fail("Worker 크래시 추정 - task_id 미설정 좀비");
                showcase3dModelPort.save(failedModel);
                log.warn("좀비 GENERATING 모델 FAILED 전환 - showcase3dModelId: {}", model.getId());
                failed++;
            } catch (RuntimeException e) {
                log.error("좀비 GENERATING 모델 FAILED 전환 실패 - showcase3dModelId: {}",
                        model.getId(), e);
            }
        }
        return failed;
    }
}
