package com.gearshow.backend.chat.adapter.in.web.dto;

import java.time.Instant;

/**
 * {@code DUPLICATE_CLIENT_MESSAGE_ID} 409 응답 바디.
 *
 * <p>api-spec §8-5 규약: 재시도 시 기존에 저장된 메시지 식별자를 반환하여
 * 클라이언트가 자체적으로 동일 메시지로 수렴할 수 있도록 한다.</p>
 */
public record DuplicatedChatMessageResponse(Long chatMessageId, long seq, Instant sentAt) {
}
