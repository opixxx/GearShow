package com.gearshow.backend.showcase.adapter.out.messaging;

/**
 * 입장 큐 drainer 가 재발행하는 메시지의 결정적 {@code messageId} 규칙 (ADR-025 NN1·NN2).
 *
 * <p>형식: {@code model-generation:drain:{workflowId}:{dispatchEpochMillis}}. 원본 parked
 * messageId(= SHA-256(Idempotency-Key))와 <b>반드시 다르다</b> — 원본은 park 시점
 * {@code markProcessed} 되어 재사용 시 Worker 의 {@code isProcessed} 컷에 걸려 전량 silent
 * skip 되기 때문(NN1).</p>
 *
 * <p>{@code dispatchEpochMillis} 는 drain-lock 안에서 1회 생성·payload 박제되어
 * {@code @RetryableTopic} 재시도 간 동일하게 유지된다(NN2). id 형식 지식은 어댑터 레이어에
 * 가두고 application 은 long 만 전달받는다.</p>
 */
public final class AdmissionDrainMessageId {

    private static final String PREFIX = "model-generation:drain:";

    private AdmissionDrainMessageId() {
    }

    /** drainer 재발행 messageId 를 생성한다 (NN1 형식). */
    public static String build(long workflowId, long dispatchEpochMillis) {
        return PREFIX + workflowId + ':' + dispatchEpochMillis;
    }

    /** drainer 가 붙인 messageId 형식인지 여부. */
    public static boolean isDrainId(String messageId) {
        return messageId != null && messageId.startsWith(PREFIX);
    }

    /**
     * messageId 에서 {@code dispatchEpochMillis} 를 추출한다 (마지막 {@code ':'} 뒤 정수).
     *
     * @throws IllegalArgumentException drain 형식이 아니거나 epoch 파싱 실패
     */
    public static long parseDispatchEpoch(String messageId) {
        if (!isDrainId(messageId)) {
            throw new IllegalArgumentException("drain messageId 형식이 아님: " + messageId);
        }
        int last = messageId.lastIndexOf(':');
        try {
            return Long.parseLong(messageId.substring(last + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("dispatchEpoch 파싱 실패: " + messageId, e);
        }
    }
}
