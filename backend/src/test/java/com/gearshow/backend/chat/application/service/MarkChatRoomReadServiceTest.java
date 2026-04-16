package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatReadMarkerPort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.ForbiddenChatRoomAccessException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatMessageException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarkChatRoomReadServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 20L;
    private static final Long MSG_ID = 100L;

    @InjectMocks
    private MarkChatRoomReadService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ChatMessagePort chatMessagePort;
    @Mock private ChatReadMarkerPort chatReadMarkerPort;

    private ChatRoom room() {
        return ChatRoom.builder().id(ROOM_ID).showcaseId(42L)
                .sellerId(10L).buyerId(USER_ID).build();
    }

    private ChatMessage messageInRoom(Long roomId) {
        return ChatMessage.builder()
                .id(MSG_ID).chatRoomId(roomId).senderId(10L).seq(5)
                .messageType(ChatMessageType.TEXT).content("c")
                .status(ChatMessageStatus.ACTIVE).sentAt(Instant.now()).build();
    }

    @Test
    @DisplayName("참여자가 정상 메시지 ID로 호출하면 upsert 호출")
    void mark_success() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.of(messageInRoom(ROOM_ID)));

        assertThatCode(() -> service.mark(ROOM_ID, USER_ID, MSG_ID)).doesNotThrowAnyException();

        verify(chatReadMarkerPort).upsert(ROOM_ID, USER_ID, MSG_ID);
    }

    @Test
    @DisplayName("채팅방 없으면 NotFoundChatRoomException")
    void mark_roomNotFound() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.mark(ROOM_ID, USER_ID, MSG_ID))
                .isInstanceOf(NotFoundChatRoomException.class);
    }

    @Test
    @DisplayName("참여자가 아니면 ForbiddenChatRoomAccessException")
    void mark_notParticipant() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));

        assertThatThrownBy(() -> service.mark(ROOM_ID, 999L, MSG_ID))
                .isInstanceOf(ForbiddenChatRoomAccessException.class);
    }

    @Test
    @DisplayName("메시지가 없으면 NotFoundChatMessageException")
    void mark_messageNotFound() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.mark(ROOM_ID, USER_ID, MSG_ID))
                .isInstanceOf(NotFoundChatMessageException.class);
    }

    @Test
    @DisplayName("메시지가 다른 채팅방 소속이면 NotFoundChatMessageException")
    void mark_messageOfOtherRoom() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.of(messageInRoom(999L)));

        assertThatThrownBy(() -> service.mark(ROOM_ID, USER_ID, MSG_ID))
                .isInstanceOf(NotFoundChatMessageException.class);
    }
}
