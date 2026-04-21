package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.application.dto.ChatRoomListProjection;
import com.gearshow.backend.chat.domain.model.ChatRoom;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 채팅방 Outbound Port.
 */
public interface ChatRoomPort {

    ChatRoom save(ChatRoom chatRoom);

    Optional<ChatRoom> findById(Long chatRoomId);

    /**
     * 새 메시지 도착 시 채팅방의 {@code lastMessageAt} 만 갱신한다.
     *
     * <p>도메인 {@link ChatRoom#touch(Instant)} 를 거치지 않고 타겟 UPDATE 로 직접 반영한다.
     * 이유:</p>
     * <ol>
     *   <li>detached entity save (= SELECT + UPDATE) 로 인한 불필요한 round-trip 제거.</li>
     *   <li>{@code WHERE last_message_at IS NULL OR last_message_at < :sentAt} 조건으로
     *       DB 수준에서 시간 역진 방지 (도메인 touch 의 if-guard 와 의미 일치).</li>
     *   <li>동시 발송 시 경합은 InnoDB row lock 으로 직렬화.</li>
     * </ol>
     *
     * @return 영향 받은 row 수. 0 이면 시간 역진 등으로 조건에 부합하지 않아 no-op.
     */
    int touchLastMessageAt(Long chatRoomId, Instant sentAt);

    /**
     * {@code (showcaseId, buyerId)} 유니크 키로 기존 채팅방을 조회한다.
     */
    Optional<ChatRoom> findByShowcaseIdAndBuyerId(Long showcaseId, Long buyerId);

    /**
     * 참여자 기준 채팅방 목록 첫 페이지.
     * {@code size + 1} 만큼 조회해 hasNext 판정에 사용한다.
     *
     * @param userId 참여자(판매자 또는 구매자) ID
     * @param size   페이지 크기
     */
    List<ChatRoomListProjection> findByParticipantFirstPage(Long userId, int size);

    /**
     * 참여자 기준 채팅방 목록 커서 페이지.
     * 정렬 기준: {@code (lastActivityAt DESC, chatRoomId DESC)}. {@code lastActivityAt}은
     * {@code COALESCE(lastMessageAt, createdAt)}로 계산한다.
     */
    List<ChatRoomListProjection> findByParticipantWithCursor(Long userId,
                                                             Instant cursorLastActivityAt,
                                                             Long cursorChatRoomId,
                                                             int size);
}
