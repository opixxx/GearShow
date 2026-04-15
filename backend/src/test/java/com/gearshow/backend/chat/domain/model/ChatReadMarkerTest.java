package com.gearshow.backend.chat.domain.model;

import com.gearshow.backend.chat.domain.exception.InvalidChatRoomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatReadMarkerTest {

    @Test
    @DisplayName("create 시 필수 값이 없으면 InvalidChatRoomException")
    void create_missingArg_throws() {
        assertThatThrownBy(() -> ChatReadMarker.create(null, 2L, 10L))
                .isInstanceOf(InvalidChatRoomException.class);
    }

    @Test
    @DisplayName("updateTo는 더 큰 값으로 갱신한다")
    void updateTo_advances() {
        ChatReadMarker m = ChatReadMarker.create(1L, 2L, 5L);

        ChatReadMarker updated = m.updateTo(10L);

        assertThat(updated.getLastReadMessageId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("updateTo는 더 작은 값으로는 역진하지 않는다")
    void updateTo_doesNotRegress() {
        ChatReadMarker m = ChatReadMarker.create(1L, 2L, 10L);

        ChatReadMarker updated = m.updateTo(5L);

        assertThat(updated.getLastReadMessageId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("updateTo에 null을 넘기면 그대로 둔다")
    void updateTo_null_noop() {
        ChatReadMarker m = ChatReadMarker.create(1L, 2L, 10L);

        ChatReadMarker updated = m.updateTo(null);

        assertThat(updated.getLastReadMessageId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("최초 생성 시 null lastReadMessageId는 모든 새 값으로 갱신 가능")
    void updateTo_fromNull() {
        ChatReadMarker m = ChatReadMarker.create(1L, 2L, null);

        ChatReadMarker updated = m.updateTo(1L);

        assertThat(updated.getLastReadMessageId()).isEqualTo(1L);
    }
}
