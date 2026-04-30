package com.gearshow.backend.showcase.adapter.in.event;

import com.gearshow.backend.showcase.application.event.Model3dCompletedEvent;
import com.gearshow.backend.showcase.application.port.in.MarkShowcaseHas3dModelUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link Model3dCompletedEvent} 를 구독해 Showcase 의 비정규화 플래그
 * {@code has_3d_model} 을 동기화한다.
 *
 * <p><b>@TransactionalEventListener(AFTER_COMMIT)</b>: TX_final 이 실제로 커밋되어야만 발화한다.
 * 롤백 시에는 발화하지 않아 도메인 행과 비정규화 플래그의 정합성이 보장된다. 본 경로의 호출자
 * ({@code DownloadFinalizer.finalizeDownload}) 는 항상 명시적 {@code @Transactional} 안에 있어
 * AFTER_COMMIT 이 의도대로 작동한다. {@code fallbackExecution=true} 는 향후 호출 컨텍스트가 바뀌어
 * TX 가 사라지더라도 회귀하지 않도록 둔 방어적 안전망 (기존 두 리스너와 동일 정책).</p>
 *
 * <p><b>@Async("workflowEventExecutor")</b>: 단일 컬럼 UPDATE 라 풀 부담은 작지만, 호출자
 * (Downloader 스레드 - downloadExecutor) 를 블로킹하지 않도록 별도 풀로 라우팅한다. 폴링 풀
 * ({@code tripoPollingExecutor}) 은 단일 consumer 라 공유하면 데드락 위험이 있어 사용 금지
 * (2026-04-28 운영 사고 참조 — {@code AsyncPollingConfig} Javadoc).</p>
 *
 * <p><b>예외 정책</b>: 리스너 본문에서 발생한 예외는 호출자(이미 커밋된 Downloader) 에 전파되지
 * 않으므로 그대로 두면 조용한 누락 (silent loss) 이 된다. 그러나 {@code RuntimeException} 을 통째로
 * catch 하면 NPE / ClassCastException 같은 프로그래밍 결함까지 흡수되어 fail-loud 원칙을 깬다.
 * 따라서 일시 장애로 분류되는 {@link DataAccessException} 만 흡수하고, 그 외 예외는
 * 그대로 전파해 {@code @Async} 의 기본 {@code AsyncUncaughtExceptionHandler} 가 ERROR 로그로
 * 노출하도록 한다.</p>
 *
 * <p><b>silent loss 가시성</b>: DB 일시 장애로 흡수된 케이스는 {@code MeterRegistry} 카운터
 * {@code showcase.has3dmodel.sync.failure} 로 누적된다. 운영에서는 이 카운터에 임계 알림을
 * 걸어 백필 SQL 실행 또는 수동 복구 시점을 결정한다. 향후 정합성 자동 보정 (Reconcile 에 누락
 * 보정 단계 추가) 으로 격상 가능 — 별도 PR.</p>
 */
@Slf4j
@Component
public class Model3dCompletedEventListener {

    private final MarkShowcaseHas3dModelUseCase markShowcaseHas3dModelUseCase;
    private final Counter syncFailureCounter;

    public Model3dCompletedEventListener(MarkShowcaseHas3dModelUseCase markShowcaseHas3dModelUseCase,
                                         MeterRegistry meterRegistry) {
        this.markShowcaseHas3dModelUseCase = markShowcaseHas3dModelUseCase;
        this.syncFailureCounter = Counter.builder("showcase.has3dmodel.sync.failure")
                .description("AFTER_COMMIT 리스너가 Showcase.has_3d_model 갱신에 실패한 횟수 "
                        + "(DB 일시 장애로 흡수된 경우). 백필 SQL 실행 신호로 활용한다.")
                .register(meterRegistry);
    }

    @Async("workflowEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCompleted(Model3dCompletedEvent event) {
        try {
            markShowcaseHas3dModelUseCase.markHas3dModel(event.showcaseId());
        } catch (DataAccessException e) {
            syncFailureCounter.increment();
            log.error("[ALERT][HAS3D_SYNC_FAIL] Showcase.has_3d_model 동기화 실패 — "
                    + "백필 SQL 또는 수동 복구 필요. showcaseId: {}", event.showcaseId(), e);
        }
    }
}
