package com.gearshow.backend.showcase.application.port.out;

/**
 * 입장 큐(ADR-025) 관측 지표 포트.
 *
 * <p>archTest 가 application 레이어의 {@code io.micrometer} 직접 의존을 금지하므로 메트릭은 본
 * 포트를 경유해 기록한다. 구현은 adapter 레이어의 Micrometer 어댑터다.</p>
 *
 * <p><b>defer-until-measured 전략의 측정 수단</b>(ADR-025 §4 D1·D3): D1(옵션 b 전환)·D3(하드캡)
 * 의 "피크 실측 시 재평가" 트리거를 측정 가능하게 만든다. 특히 {@link #recordInflight(long)} 는
 * NN7({@code requested-stuck-seconds} 가 현실 in-flight + 첫 retry backoff 보다 위인지) 의
 * 직접 판정 지표다.</p>
 */
public interface AdmissionMetricsPort {

    /** cap 초과로 {@code tripo:queue} 에 park 됐을 때. */
    void parkOccurred();

    /**
     * drained 워크플로우가 {@code REQUESTED → PREPARING} 으로 실제 입장한 순간 호출한다.
     * 드레이너 재발행 시각부터 입장까지의 지연(in-flight latency)을 기록한다 (ADR-025 NN7).
     *
     * @param dispatchEpochMillis 드레이너가 재발행한 시각(epoch millis)
     */
    void recordInflight(long dispatchEpochMillis);

    /**
     * 드레이너가 Kafka 로 재발행 송신한 건수.
     *
     * @param count 이번 drain tick 에서 송신한 건수
     */
    void dispatched(int count);

    /** bypass 경로에서 {@code REQUESTED → PREPARING} 조건부 UPDATE affected=0 (N1 중복 디스패치 관측). */
    void bypassSkipped();

    /** 드레이너 async 재발행 실패(send 콜백). */
    void republishFailed();
}
