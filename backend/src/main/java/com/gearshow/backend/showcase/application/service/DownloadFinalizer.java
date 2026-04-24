package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TX_final 을 단일 트랜잭션으로 수행하는 협력자 (설계 §7 [8]).
 *
 * <p>분리 이유: {@link DownloadAndMirrorService} 에 직접 {@code @Transactional} 을 걸면 {@code public
 * download()} 가 Tripo GET · S3 PUT 까지 TX 에 포함시켜 락 밖 외부 I/O 원칙을 깬다. 반대로 private
 * 헬퍼에 어노테이션을 붙이면 Spring AOP self-invocation 때문에 TX 가 무력화된다. 별도 컴포넌트로
 * 빼면 호출 지점에서 프록시가 정상 개입해 `markCompleted` · `markCompletedByShowcaseId` ·
 * `publishCompleted` 3 개 호출이 한 트랜잭션으로 묶이며, Outbox Pattern 의 "DB 커밋 = Kafka 발행
 * 보장" 불변식이 성립한다.</p>
 *
 * <p><b>실행 순서</b>:</p>
 * <ol>
 *   <li>{@code workflowPort.markCompleted} affected=0 이면 즉시 false (다른 Downloader 선점)</li>
 *   <li>{@code showcase3dModelPort.markCompletedByShowcaseId} affected=0 이면 ALERT 로그 + false
 *       (도메인 행 누락 — Reconcile 복구 대상, Outbox 발행하지 않음으로 잘못된 완료 알림 차단)</li>
 *   <li>{@code eventPublisher.publishCompleted} — Outbox INSERT (같은 TX)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadFinalizer {

    private final ModelGenerationWorkflowPort workflowPort;
    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelGenerationEventPublisher eventPublisher;

    /**
     * TX_final 을 단일 트랜잭션으로 실행한다. 호출자가 {@code workflow:lock} 을 보유한 상태에서
     * 호출해야 한다 (외부 I/O 는 반드시 락 밖).
     *
     * @return 완료 이벤트까지 발행된 경우 {@code true}. affected=0 으로 skip 된 모든 경로는 {@code false}.
     */
    @Transactional
    public boolean finalizeDownload(Long workflowId, Long showcaseId, GenerationResult result) {
        int workflowAffected = workflowPort.markCompleted(workflowId);
        if (workflowAffected == 0) {
            log.info("markCompleted affected=0 — 다른 Downloader 가 이미 처리. skip. "
                    + "workflowId: {}", workflowId);
            return false;
        }
        Instant generatedAt = Instant.now();
        int showcaseAffected = showcase3dModelPort.markCompletedByShowcaseId(
                showcaseId, result.modelFileUrl(), result.previewImageUrl(), generatedAt);
        if (showcaseAffected == 0) {
            log.error("ALERT: Showcase3dModel 행 누락 — Outbox 발행 차단. Reconcile 복구 대상. "
                    + "workflowId: {}, showcaseId: {}", workflowId, showcaseId);
            // 도메인 행이 없는 상태로 COMPLETED 이벤트를 발행하면 후속 FCM 이 URL 없는 알림을
            // 보낸다. TX 전체를 롤백시키기 위해 예외를 던진다.
            throw new IllegalStateException(
                    "Showcase3dModel 행 누락 — workflowId: " + workflowId);
        }
        eventPublisher.publishCompleted(
                workflowId, showcaseId, result.modelFileUrl(), result.previewImageUrl());
        log.info("TX_final 완료 — workflowId: {}, showcaseId: {}, modelUrl: {}",
                workflowId, showcaseId, result.modelFileUrl());
        return true;
    }
}
