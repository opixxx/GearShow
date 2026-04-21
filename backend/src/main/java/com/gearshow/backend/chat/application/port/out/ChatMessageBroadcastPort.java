package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.application.dto.ChatMessageBroadcastPayload;

/**
 * 채팅 메시지 브로드캐스트 아웃바운드 포트.
 *
 * <p>REST · WebSocket 등 어느 진입점에서 수신한 메시지든 최종 브로드캐스트는
 * 이 포트를 경유해 단일 어댑터({@code StompBroadcastAdapter} 등)에서 처리된다.
 * 이로써 어댑터 간 브로드캐스트 로직 중복을 제거하고, 향후 Kafka Relay 등으로
 * 전송 수단을 바꿔도 포트 구현체만 교체하면 된다.</p>
 *
 * <p>호출은 반드시 {@code @TransactionalEventListener(AFTER_COMMIT)} 경유로만 수행한다
 * (ADR-009). 직접 호출하면 트랜잭션 롤백 시 유령 메시지 발생 위험이 있다.</p>
 */
public interface ChatMessageBroadcastPort {

    /**
     * 채팅 메시지를 해당 채팅방 토픽으로 브로드캐스트 한다.
     *
     * @param payload 브로드캐스트 대상 정보 (도메인 타입을 직접 노출하지 않기 위한 경량 DTO)
     */
    void publish(ChatMessageBroadcastPayload payload);
}
