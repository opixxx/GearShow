package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.model.ChatReadMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatReadMarkerPersistenceAdapterIntegrationTest {

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 20L;

    @Autowired private ChatReadMarkerJpaRepository chatReadMarkerJpaRepository;

    private ChatReadMarkerPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ChatReadMarkerPersistenceAdapter(
                chatReadMarkerJpaRepository, new ChatReadMarkerMapper());
    }

    @Test
    @DisplayName("upsert: 기존 마커가 없으면 INSERT")
    void upsert_insert() {
        ChatReadMarker result = adapter.upsert(ROOM_ID, USER_ID, 100L);

        assertThat(result.getLastReadMessageId()).isEqualTo(100L);
        Optional<ChatReadMarker> found = adapter.findByChatRoomIdAndUserId(ROOM_ID, USER_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getLastReadMessageId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("upsert: 더 큰 값이 들어오면 GREATEST로 갱신")
    void upsert_advances() {
        adapter.upsert(ROOM_ID, USER_ID, 100L);

        ChatReadMarker result = adapter.upsert(ROOM_ID, USER_ID, 200L);

        assertThat(result.getLastReadMessageId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("upsert: 더 작은 값(역진)은 GREATEST로 무시")
    void upsert_doesNotRegress() {
        adapter.upsert(ROOM_ID, USER_ID, 200L);

        ChatReadMarker result = adapter.upsert(ROOM_ID, USER_ID, 50L);

        assertThat(result.getLastReadMessageId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("upsert: 동일 사용자 다른 채팅방은 별도 row로 관리")
    void upsert_isolatedPerRoomAndUser() {
        adapter.upsert(ROOM_ID, USER_ID, 100L);
        adapter.upsert(2L, USER_ID, 50L);

        assertThat(adapter.findByChatRoomIdAndUserId(ROOM_ID, USER_ID).get().getLastReadMessageId())
                .isEqualTo(100L);
        assertThat(adapter.findByChatRoomIdAndUserId(2L, USER_ID).get().getLastReadMessageId())
                .isEqualTo(50L);
    }
}
