package com.gearshow.backend.showcase.adapter.out.outbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 3D 모델 생성 워크플로우의 Outbox {@code event_id} (= 메시지 {@code messageId}) 를
 * 결정적으로 파생한다 (ADR-011 ③).
 *
 * <p>같은 {@code Idempotency-Key} 에 대해 항상 동일한 event_id 를 보장해,
 * Consumer 의 {@code processed_message} UNIQUE 제약이 브로커 재전송 · rebalance 로 인한
 * 중복 소비를 차단할 수 있게 한다.</p>
 *
 * <p>알고리즘: SHA-256, 출력 형식: hex lowercase 64자 ({@code outbox.event_id VARCHAR(64)}
 * 컬럼 폭과 정확히 일치, 설계 문서 §3.1).</p>
 */
final class ShowcaseWorkflowEventIds {

    private static final String ALGORITHM = "SHA-256";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private ShowcaseWorkflowEventIds() {
    }

    /**
     * 완료 이벤트 {@code event_id} 를 결정적으로 파생한다 (P1-F, 설계 §7 [8]). TX_final 재진입 시
     * 동일한 {@code messageId} 를 생성해 Consumer {@code processed_message} 중복 차단이 작동한다.
     *
     * <p>공식: {@code SHA-256("workflow:{workflowId}:COMPLETED")}. 문자열 리터럴 오타 방지를 위해
     * 반드시 이 헬퍼를 경유한다.</p>
     */
    static String forCompletionEvent(Long workflowId) {
        if (workflowId == null) {
            throw new IllegalArgumentException("workflowId 는 필수입니다");
        }
        return deriveMessageId("workflow:" + workflowId + ":COMPLETED");
    }

    /**
     * 멱등성 키로부터 결정적 event_id 를 계산한다.
     *
     * @param idempotencyKey API 멱등성 키 (non-null, non-blank)
     * @return SHA-256 hex lowercase 64자 문자열
     * @throws IllegalArgumentException idempotencyKey 가 null 이거나 공백인 경우
     * @throws IllegalStateException    JVM 이 SHA-256 을 지원하지 않는 경우 (발생 불가)
     */
    static String deriveMessageId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 는 필수입니다");
        }
        byte[] digest = newDigest().digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        return toHexLowerCase(digest);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " 알고리즘을 찾을 수 없습니다", e);
        }
    }

    private static String toHexLowerCase(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_DIGITS[v >>> 4];
            chars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(chars);
    }
}
