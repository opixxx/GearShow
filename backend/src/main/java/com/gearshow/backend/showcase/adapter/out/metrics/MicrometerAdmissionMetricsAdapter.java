package com.gearshow.backend.showcase.adapter.out.metrics;

import com.gearshow.backend.showcase.application.port.out.AdmissionMetricsPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link AdmissionMetricsPort} 의 Micrometer 구현 (ADR-025 Counters).
 *
 * <p>{@code gearshow.admission.queue.size} gauge 는 {@link TripoAdmissionQueuePort#size()} 에
 * 바인딩되어 스크래핑 시점마다 평가된다. 나머지는 counter/timer 다. 대시보드·알람은 P1-H
 * deferred 이나 본 메트릭 자체는 ADR-025 의 defer-until-measured 트리거 측정 수단으로 필수다.</p>
 */
@Component
public class MicrometerAdmissionMetricsAdapter implements AdmissionMetricsPort {

    private final Counter parkCounter;
    private final Counter dispatchedCounter;
    private final Counter bypassSkippedCounter;
    private final Counter republishFailedCounter;
    private final Timer inflightTimer;

    public MicrometerAdmissionMetricsAdapter(MeterRegistry meterRegistry,
                                             TripoAdmissionQueuePort admissionQueuePort) {
        this.parkCounter = Counter.builder("gearshow.admission.park.count")
                .description("cap 초과로 tripo:queue 에 park 된 건수")
                .register(meterRegistry);
        this.dispatchedCounter = Counter.builder("gearshow.admission.drain.dispatched.count")
                .description("드레이너 Kafka 재발행 송신 *시도* 건수 (async). "
                        + "실제 입장 ≈ dispatched - republish.failed")
                .register(meterRegistry);
        this.bypassSkippedCounter = Counter.builder("gearshow.admission.bypass.skipped.count")
                .description("bypass 경로 REQUESTED→PREPARING affected=0 (N1 중복 디스패치 관측)")
                .register(meterRegistry);
        this.republishFailedCounter = Counter.builder("gearshow.admission.republish.failed.count")
                .description("드레이너 async 재발행 실패")
                .register(meterRegistry);
        this.inflightTimer = Timer.builder("gearshow.admission.inflight")
                .description("drained dispatch → REQUESTED→PREPARING 입장 지연 (NN7 판정 지표)")
                .register(meterRegistry);
        // 가우지: ZSET 대기열 깊이. state object 는 Spring 싱글톤이라 weak ref 회수 우려 없음.
        meterRegistry.gauge("gearshow.admission.queue.size", admissionQueuePort,
                p -> (double) p.size());
    }

    @Override
    public void parkOccurred() {
        parkCounter.increment();
    }

    @Override
    public void recordInflight(long dispatchEpochMillis) {
        long elapsed = System.currentTimeMillis() - dispatchEpochMillis;
        // 음수(시계 역행/오염 입력)는 기록하지 않는다.
        if (elapsed >= 0) {
            inflightTimer.record(Duration.ofMillis(elapsed));
        }
    }

    @Override
    public void dispatched(int count) {
        if (count > 0) {
            dispatchedCounter.increment(count);
        }
    }

    @Override
    public void bypassSkipped() {
        bypassSkippedCounter.increment();
    }

    @Override
    public void republishFailed() {
        republishFailedCounter.increment();
    }
}
