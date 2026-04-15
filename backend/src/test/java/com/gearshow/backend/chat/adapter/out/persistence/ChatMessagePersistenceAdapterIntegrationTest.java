package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatMessagePersistenceAdapterIntegrationTest {

    private static final Long ROOM_ID = 1L;
    private static final Long SENDER = 10L;

    @Autowired private ChatMessageJpaRepository chatMessageJpaRepository;

    private ChatMessagePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ChatMessagePersistenceAdapter(chatMessageJpaRepository, new ChatMessageMapper());
    }

    @Test
    @DisplayName("save 후 findById로 조회 가능")
    void save_and_findById() {
        ChatMessage saved = adapter.save(ChatMessage.text(ROOM_ID, SENDER, 1L, "hi", null));

        Optional<ChatMessage> found = adapter.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("hi");
    }

    @Test
    @DisplayName("nextSeq: 빈 채팅방은 1을 반환")
    void nextSeq_emptyRoom_returns1() {
        long seq = adapter.nextSeq(999L);

        assertThat(seq).isEqualTo(1L);
    }

    @Test
    @DisplayName("nextSeq: 메시지 있으면 MAX+1")
    void nextSeq_existing_returnsMaxPlusOne() {
        adapter.save(ChatMessage.text(ROOM_ID, SENDER, 1L, "a", null));
        adapter.save(ChatMessage.text(ROOM_ID, SENDER, 2L, "b", null));

        long seq = adapter.nextSeq(ROOM_ID);

        assertThat(seq).isEqualTo(3L);
    }

    @Test
    @DisplayName("UNIQUE(chat_room_id, seq) 충돌 시 DataIntegrityViolationException")
    void uniqueSeq_violation_throws() {
        adapter.save(ChatMessage.text(ROOM_ID, SENDER, 5L, "a", null));
        ChatMessage duplicate = ChatMessage.text(ROOM_ID, SENDER, 5L, "b", "diff-client-id");

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByClientMessageId: 동일 (room, sender, clientMessageId) 조회")
    void findByClientMessageId_finds() {
        adapter.save(ChatMessage.text(ROOM_ID, SENDER, 1L, "hi", "uuid-123"));

        Optional<ChatMessage> found = adapter.findByClientMessageId(ROOM_ID, SENDER, "uuid-123");

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("findByClientMessageId: clientMessageId가 null이면 빈 Optional")
    void findByClientMessageId_null_returnsEmpty() {
        Optional<ChatMessage> found = adapter.findByClientMessageId(ROOM_ID, SENDER, null);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByChatRoomIdFirstPage: DESC 정렬로 size+1 조회")
    void findFirstPage_descOrder() {
        for (long s = 1; s <= 5; s++) {
            adapter.save(ChatMessage.text(ROOM_ID, SENDER, s, "m" + s, null));
        }

        List<ChatMessage> rows = adapter.findByChatRoomIdFirstPage(ROOM_ID, 3);

        assertThat(rows).hasSize(4); // size+1
        assertThat(rows.get(0).getSeq()).isEqualTo(5L);
        assertThat(rows.get(rows.size() - 1).getSeq()).isLessThan(rows.get(0).getSeq());
    }

    @Test
    @DisplayName("findByChatRoomIdBefore: 특정 ID 이전 메시지만 반환")
    void findBefore() {
        ChatMessage m1 = adapter.save(ChatMessage.text(ROOM_ID, SENDER, 1L, "a", null));
        ChatMessage m2 = adapter.save(ChatMessage.text(ROOM_ID, SENDER, 2L, "b", null));
        adapter.save(ChatMessage.text(ROOM_ID, SENDER, 3L, "c", null));

        List<ChatMessage> rows = adapter.findByChatRoomIdBefore(ROOM_ID, m2.getId(), 10);

        assertThat(rows).extracting(ChatMessage::getId).containsExactly(m1.getId());
    }
}
