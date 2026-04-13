package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.RecoverStuckModelsUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
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
 * <p><b>설계 결정 #3 (Recovery 대상 명확화)</b> 에 따른 세 가지 복구 시나리오:</p>
 * <ol>
 *   <li><b>REQUESTED stuck</b>: Outbox 의 안전망이 정상 동작하면 보통 수 초 내에 Worker 가
 *       집어간다. 5분 넘게 REQUESTED 인 모델은 예외 케이스 — Outbox 재등록을 통해 다시 발행한다.</li>
 *   <li><b>PREPARING stuck (좀비)</b>: Worker 가 Tripo 호출 전에 크래시한 케이스.
 *       taskId=NULL 이므로 Tripo 과금이 발생하지 않았음이 보장된다.
 *       retryCount 가 3 미만이면 REQUESTED 로 되돌려 자동 재시도하고,
 *       3 이상이면 FAILED + Alert 로 전환한다.</li>
 *   <li><b>GENERATING + task_id 없음 (비정상)</b>: 정상 코드 흐름에서는 발생할 수 없는 상태.
 *       (GENERATING 전환 시 taskId 가 반드시 함께 저장됨.)
 *       발견 시 즉시 FAILED + Alert 로 전환하여 개발자가 확인하게 한다.</li>
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
        recovered += recoverStuckPreparing();
        recovered += failAnomalousGenerating();
        return recovered;
    }

    /**
     * REQUESTED 상태에서 오래 머물러 있는 모델을 Outbox 에 재등록한다.
     *
     * @return 실제로 재등록에 성공한 모델 수
     */
    private int recoverStuckRequested() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.requestedStuckMinutes()));

        List<Showcase3dModel> stuck = showcase3dModelPort
                .findStaleByStatus(ModelStatus.REQUESTED, threshold, properties.batchSize());

        int recovered = 0;
        for (Showcase3dModel model : stuck) {
            try {
                modelGenerationEventPublisher.publishRequested(model.getId(), model.getShowcaseId());
                log.warn("REQUESTED stuck 복구 - Outbox 재등록 - showcase3dModelId: {}",
                        model.getId());
                recovered++;
            } catch (RuntimeException e) {
                log.error("REQUESTED stuck 복구 실패 - showcase3dModelId: {}", model.getId(), e);
            }
        }
        return recovered;
    }

    /**
     * PREPARING 상태에서 오래 머물러 있는 모델을 자동 재시도한다.
     *
     * <p><b>설계 결정 #2</b>: PREPARING + taskId=NULL 은 Tripo 과금이 발생하지 않았음을 보장한다.
     * 따라서 안전하게 REQUESTED 로 되돌려 재시도할 수 있다.</p>
     *
     * <p>retryCount 가 최대치를 초과하면 FAILED + Alert 로 전환하여 무한 루프를 방지한다.</p>
     *
     * @return 실제로 복구/FAILED 처리한 모델 수
     */
    private int recoverStuckPreparing() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.requestedStuckMinutes()));

        List<Showcase3dModel> stuck = showcase3dModelPort
                .findStaleByStatus(ModelStatus.PREPARING, threshold, properties.batchSize());

        int recovered = 0;
        for (Showcase3dModel model : stuck) {
            try {
                if (model.isMaxRetryExceeded()) {
                    // retryCount >= 3 → 무한 루프 방지, FAILED + Alert
                    Showcase3dModel failed = model.fail(
                            "PREPARING 상태 자동 재시도 " + model.getRetryCount() + "회 초과");
                    showcase3dModelPort.save(failed);
                    log.error("ALERT: PREPARING stuck 최대 재시도 초과 - FAILED 전환 - "
                            + "showcase3dModelId: {}, retryCount: {}",
                            model.getId(), model.getRetryCount());
                } else {
                    // retryCount < 3 → REQUESTED 로 되돌리고 Outbox 재등록
                    Showcase3dModel reset = model.resetForRetry(model.getGenerationProvider());
                    showcase3dModelPort.save(reset);
                    modelGenerationEventPublisher.publishRequested(
                            model.getId(), model.getShowcaseId());
                    log.warn("PREPARING stuck 복구 - 자동 재시도 (retryCount: {} → {}) "
                                    + "- showcase3dModelId: {}",
                            model.getRetryCount(), model.getRetryCount() + 1, model.getId());
                }
                recovered++;
            } catch (RuntimeException e) {
                log.error("PREPARING stuck 복구 실패 - showcase3dModelId: {}", model.getId(), e);
            }
        }
        return recovered;
    }

    /**
     * GENERATING 상태이지만 task_id 가 없는 비정상 모델을 FAILED 로 전환한다.
     *
     * <p><b>설계 결정 #3</b>: GENERATING + taskId=NULL 은 정상 코드 흐름에서 발생할 수 없다
     * (GENERATING 전환 시 taskId 가 반드시 함께 저장됨). 이 상태가 발견되면 코드 버그 또는
     * 인프라 이상이므로 즉시 FAILED + Alert 로 전환하여 개발자가 확인하게 한다.</p>
     *
     * <p>자동 재시도하지 않는 이유: taskId 가 실제로는 Tripo 에 생성됐을 가능성을 배제할 수 없어
     * 재시도 시 이중 과금 위험이 있다.</p>
     */
    private int failAnomalousGenerating() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.generatingWithoutTaskIdStuckMinutes()));

        List<Showcase3dModel> anomalous = showcase3dModelPort
                .findStaleGeneratingWithoutTaskId(threshold, properties.batchSize());

        int failed = 0;
        for (Showcase3dModel model : anomalous) {
            try {
                Showcase3dModel failedModel = model.fail(
                        "비정상 상태 감지 - GENERATING 이지만 task_id 없음 (개발자 확인 필요)");
                showcase3dModelPort.save(failedModel);
                log.error("ALERT: 비정상 GENERATING+taskId=NULL 감지 - FAILED 전환 "
                        + "- showcase3dModelId: {}", model.getId());
                failed++;
            } catch (RuntimeException e) {
                log.error("비정상 GENERATING 모델 FAILED 전환 실패 - showcase3dModelId: {}",
                        model.getId(), e);
            }
        }
        return failed;
    }
}
