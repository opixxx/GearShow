package com.gearshow.backend.chat.application.event;

import com.gearshow.backend.chat.application.dto.ChatMessageBroadcastPayload;

/**
 * 메시지가 저장 트랜잭션에 포함되었음을 알리는 application event.
 *
 * <p>{@code SendChatMessageService} 가 저장 직후 이 이벤트를 발행하고,
 * {@code ChatMessageBroadcastListener} 가 {@code @TransactionalEventListener(AFTER_COMMIT)} 로
 * 수신해 브로드캐스트 한다 (ADR-009). 이로써 트랜잭션이 롤백되면 브로드캐스트가
 * 구조적으로 발생하지 않는다.</p>
 */
public record ChatMessageCreatedEvent(ChatMessageBroadcastPayload payload) {
}
