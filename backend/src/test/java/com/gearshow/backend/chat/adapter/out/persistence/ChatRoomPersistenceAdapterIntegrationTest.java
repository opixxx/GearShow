package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.application.dto.ChatRoomListProjection;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRoomPersistenceAdapterIntegrationTest {

    @Autowired private ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired private ChatMessageJpaRepository chatMessageJpaRepository;

    private ChatRoomPersistenceAdapter adapter;
    private ChatMessagePersistenceAdapter messageAdapter;

    @BeforeEach
    void setUp() {
        adapter = new ChatRoomPersistenceAdapter(
                chatRoomJpaRepository, chatMessageJpaRepository, new ChatRoomMapper());
        messageAdapter = new ChatMessagePersistenceAdapter(
                chatMessageJpaRepository, new ChatMessageMapper());
    }

    @Test
    @DisplayName("save 후 findById로 조회 가능하고 lastMessageAt이 createdAt으로 채워진다")
    void save_and_findById() {
        ChatRoom room = ChatRoom.open(42L, 1L, 2L);

        ChatRoom saved = adapter.save(room);

        Optional<ChatRoom> found = adapter.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getLastMessageAt()).isNotNull();
        assertThat(found.get().getLastMessageAt()).isEqualTo(found.get().getCreatedAt());
    }

    @Test
    @DisplayName("findByShowcaseIdAndBuyerId 유니크 키 조회")
    void findByShowcaseIdAndBuyerId() {
        adapter.save(ChatRoom.open(42L, 1L, 2L));

        Optional<ChatRoom> found = adapter.findByShowcaseIdAndBuyerId(42L, 2L);

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("findByParticipant: 유저가 seller·buyer 양쪽으로 참여하면 모두 반환")
    void findByParticipant_includesBothRoles() {
        adapter.save(ChatRoom.open(100L, 1L, 2L));    // 1=seller
        adapter.save(ChatRoom.open(200L, 5L, 1L));    // 1=buyer

        List<ChatRoomListProjection> rows = adapter.findByParticipantFirstPage(1L, 10);

        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("findByParticipant: lastMessageAt DESC 정렬 + last message 스냅샷·unread count 합쳐 반환")
    void findByParticipant_assemblesProjection() throws InterruptedException {
        // Given: 2개 채팅방, 두 번째 방에 메시지 2건 (peer 발신 1, 본인 발신 1)
        ChatRoom room1 = adapter.save(ChatRoom.open(100L, 1L, 2L));
        Thread.sleep(5);
        ChatRoom room2 = adapter.save(ChatRoom.open(200L, 1L, 2L));
        com.gearshow.backend.chat.domain.model.ChatMessage m1 =
                com.gearshow.backend.chat.domain.model.ChatMessage.text(room2.getId(), 1L, 1L, "from-seller", null);
        com.gearshow.backend.chat.domain.model.ChatMessage m2 =
                com.gearshow.backend.chat.domain.model.ChatMessage.text(room2.getId(), 2L, 2L, "from-buyer", null);
        messageAdapter.save(m1);
        messageAdapter.save(m2);
        adapter.save(adapter.findById(room2.getId()).orElseThrow().touch(java.time.Instant.now()));

        // When: buyer(2L) 기준 목록
        List<ChatRoomListProjection> rows = adapter.findByParticipantFirstPage(2L, 10);

        // Then: room2가 더 최신 → 첫 번째
        assertThat(rows.get(0).chatRoomId()).isEqualTo(room2.getId());
        assertThat(rows.get(0).lastMessageContent()).isEqualTo("from-buyer");
        // unread: peer 발신 1건 (m1)
        assertThat(rows.get(0).unreadCount()).isEqualTo(1L);
        // room1은 메시지 없음 → lastMessageId null
        assertThat(rows.get(1).lastMessageId()).isNull();
        assertThat(rows.get(1).unreadCount()).isZero();
    }

    @Test
    @DisplayName("findByParticipantWithCursor: 커서 기준 오래된 페이지 조회")
    void findByParticipantWithCursor() throws InterruptedException {
        ChatRoom r1 = adapter.save(ChatRoom.open(100L, 1L, 2L));
        Thread.sleep(5);
        ChatRoom r2 = adapter.save(ChatRoom.open(200L, 1L, 2L));

        // 첫 페이지 size=1 → r2(더 최신)
        List<ChatRoomListProjection> first = adapter.findByParticipantFirstPage(2L, 1);
        ChatRoomListProjection cursor = first.get(0);
        assertThat(cursor.chatRoomId()).isEqualTo(r2.getId());

        // 커서 이후(=오래된 방향) 페이지 → r1만
        List<ChatRoomListProjection> next = adapter.findByParticipantWithCursor(
                2L, cursor.lastMessageAt(), cursor.chatRoomId(), 10);

        assertThat(next).extracting(ChatRoomListProjection::chatRoomId)
                .containsExactly(r1.getId());
    }
}
