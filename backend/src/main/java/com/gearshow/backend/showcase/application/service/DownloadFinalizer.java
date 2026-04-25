package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TX_final 을 단일 트랜잭션으로 수행하는 협력자 (설계 §7 [8]).
 *
 * <p><b>ADR-010 프로세스 분리 반영</b>: {@code Showcase3dModel} 은 완성품 전용이므로 기존의
 * "상태 UPDATE" 가 아니라 UPSERT({@link Showcase3dModelPort#save}) 로 기록한다. 따라서 도메인
 * 행 누락을 일시적 경고가 아닌 자연스러운 INSERT 로 처리하고, ALERT 분기를 제거했다.</p>
 *
 * <p><b>실행 순서</b>:</p>
 * <ol>
 *   <li>{@code workflowPort.markCompleted} affected=0 이면 즉시 false (다른 Downloader 선점)</li>
 *   <li>{@code showcase3dModelPort.save(Showcase3dModel.create(...))} UPSERT</li>
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
     * @return 완료 이벤트까지 발행된 경우 {@code true}. affected=0 으로 skip 된 경로는 {@code false}.
     */
    @Transactional
    public boolean finalizeDownload(Long workflowId, Long showcaseId, GenerationResult result) {
        int workflowAffected = workflowPort.markCompleted(workflowId);
        if (workflowAffected == 0) {
            log.info("markCompleted affected=0 — 다른 Downloader 가 이미 처리. skip. "
                    + "workflowId: {}", workflowId);
            return false;
        }
        showcase3dModelPort.save(Showcase3dModel.create(
                showcaseId, result.modelFileUrl(), result.previewImageUrl()));
        eventPublisher.publishCompleted(
                workflowId, showcaseId, result.modelFileUrl(), result.previewImageUrl());
        log.info("TX_final 완료 — workflowId: {}, showcaseId: {}, modelUrl: {}",
                workflowId, showcaseId, result.modelFileUrl());
        return true;
    }
}
