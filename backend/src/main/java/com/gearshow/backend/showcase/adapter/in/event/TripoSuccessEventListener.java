package com.gearshow.backend.showcase.adapter.in.event;

import com.gearshow.backend.showcase.application.event.TripoSuccessEvent;
import com.gearshow.backend.showcase.application.port.in.DownloadAndMirrorUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Poller 가 {@code markTripoSucceeded} affected=1 에서 발행한 {@link TripoSuccessEvent} 를 구독해
 * Downloader 체인을 트리거한다 (설계 §7 [8]).
 *
 * <p><b>@TransactionalEventListener(AFTER_COMMIT)</b>: Poller 경로에는 현재 상위 TX 가 없지만
 * ({@link com.gearshow.backend.showcase.adapter.out.persistence.ModelGenerationWorkflowPersistenceAdapter}
 * 의 메서드 레벨 TX 만 존재), {@code fallbackExecution=true} 로 설정해 TX 없이 발행된 이벤트도
 * 정상 수신한다. 향후 상위 TX 가 추가되더라도 "DB 커밋 = Downloader 시작" 계약이 유지된다.</p>
 *
 * <p><b>@Async("downloadExecutor")</b>: Tripo GET + S3 PUT 은 수 초~수십 초 블로킹 I/O 이므로
 * Poller 스레드와 분리된 전용 실행기에서 돌린다. caller-runs 정책으로 큐 오버플로 시 Poller 가
 * 자연스럽게 느려진다 (설계 §6.2 백프레셔).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripoSuccessEventListener {

    private final DownloadAndMirrorUseCase downloadAndMirrorUseCase;

    @Async("downloadExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripoSuccess(TripoSuccessEvent event) {
        log.info("TripoSuccessEvent 수신 — Downloader 실행. workflowId: {}, taskId: {}",
                event.workflowId(), event.tripoTaskId());
        downloadAndMirrorUseCase.download(event.workflowId(), event.tripoTaskId());
    }
}
