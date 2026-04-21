package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.event.ChatMessageCreatedEvent;
import com.gearshow.backend.chat.application.port.out.ChatMessageBroadcastPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link ChatMessageCreatedEvent} 를 트랜잭션 커밋 후에 수신하여 브로드캐스트 포트를
 * 호출한다.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} 단계로 한정했기 때문에, 메시지 저장이 롤백되면
 * 리스너 자체가 호출되지 않아 "DB 엔 없는데 클라이언트 엔 전달된" 유령 메시지가
 * 구조적으로 발생하지 않는다 (ADR-009 참고).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageBroadcastListener {

    private final ChatMessageBroadcastPort broadcastPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ChatMessageCreatedEvent event) {
        try {
            broadcastPort.publish(event.payload());
        } catch (RuntimeException ex) {
            // 브로드캐스트 실패는 DB 저장 성공을 취소시키면 안 된다 (이미 커밋됨).
            // 사용자에게는 다음 메시지 · 재접속 시 동기화로 자연 회복.
            log.warn("채팅 메시지 브로드캐스트 실패: chatRoomId={}, chatMessageId={}",
                    event.payload().chatRoomId(), event.payload().chatMessageId(), ex);
        }
    }
}
