package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.ChatMessageNotOwnerException;
import com.gearshow.backend.chat.domain.exception.ChatMessageSystemUndeletableException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatMessageException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteChatMessageServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long OWNER = 20L;
    private static final Long MSG_ID = 100L;

    @InjectMocks
    private DeleteChatMessageService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ChatMessagePort chatMessagePort;

    private ChatRoom room() {
        return ChatRoom.builder().id(ROOM_ID).showcaseId(42L).sellerId(10L).buyerId(OWNER).build();
    }

    private ChatMessage textMessage(Long sender, Long roomId) {
        return ChatMessage.builder()
                .id(MSG_ID).chatRoomId(roomId).senderId(sender).seq(5)
                .messageType(ChatMessageType.TEXT).content("c")
                .status(ChatMessageStatus.ACTIVE).sentAt(Instant.now()).build();
    }

    @Test
    @DisplayName("본인 메시지를 삭제하면 DELETED 상태로 저장")
    void delete_success() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.of(textMessage(OWNER, ROOM_ID)));

        service.delete(ROOM_ID, MSG_ID, OWNER);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessagePort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChatMessageStatus.DELETED);
    }

    @Test
    @DisplayName("채팅방 없으면 NotFoundChatRoomException")
    void delete_roomNotFound() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ROOM_ID, MSG_ID, OWNER))
                .isInstanceOf(NotFoundChatRoomException.class);
    }

    @Test
    @DisplayName("메시지가 다른 채팅방 소속이면 NotFoundChatMessageException")
    void delete_messageOfOtherRoom() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.of(textMessage(OWNER, 999L)));

        assertThatThrownBy(() -> service.delete(ROOM_ID, MSG_ID, OWNER))
                .isInstanceOf(NotFoundChatMessageException.class);
    }

    @Test
    @DisplayName("타인 메시지 삭제 시도 시 ChatMessageNotOwnerException")
    void delete_notOwner() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.of(textMessage(10L, ROOM_ID)));

        assertThatThrownBy(() -> service.delete(ROOM_ID, MSG_ID, OWNER))
                .isInstanceOf(ChatMessageNotOwnerException.class);
    }

    @Test
    @DisplayName("시스템 메시지는 ChatMessageSystemUndeletableException")
    void delete_systemMessage() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        ChatMessage system = ChatMessage.builder()
                .id(MSG_ID).chatRoomId(ROOM_ID).senderId(OWNER).seq(5)
                .messageType(ChatMessageType.SYSTEM_TICKET_ISSUED).content("ticket")
                .status(ChatMessageStatus.ACTIVE).sentAt(Instant.now()).build();
        given(chatMessagePort.findById(MSG_ID)).willReturn(Optional.of(system));

        assertThatThrownBy(() -> service.delete(ROOM_ID, MSG_ID, OWNER))
                .isInstanceOf(ChatMessageSystemUndeletableException.class);
    }
}
