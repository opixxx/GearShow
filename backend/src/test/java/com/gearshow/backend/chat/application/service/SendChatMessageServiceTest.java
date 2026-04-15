package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.dto.SendChatMessageResult;
import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.ChatMessageTooLongException;
import com.gearshow.backend.chat.domain.exception.ChatRoomClosedException;
import com.gearshow.backend.chat.domain.exception.DuplicateClientMessageIdException;
import com.gearshow.backend.chat.domain.exception.ForbiddenChatRoomAccessException;
import com.gearshow.backend.chat.domain.exception.InvalidChatMessageException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SendChatMessageServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long SELLER = 10L;
    private static final Long BUYER = 20L;

    @InjectMocks
    private SendChatMessageService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ChatMessagePort chatMessagePort;

    private ChatRoom activeRoom() {
        return ChatRoom.builder()
                .id(ROOM_ID).showcaseId(42L).sellerId(SELLER).buyerId(BUYER)
                .status(ChatRoomStatus.ACTIVE).createdAt(Instant.now())
                .lastMessageAt(Instant.now()).build();
    }

    private ChatRoom closedRoom() {
        return ChatRoom.builder()
                .id(ROOM_ID).showcaseId(42L).sellerId(SELLER).buyerId(BUYER)
                .status(ChatRoomStatus.CLOSED).createdAt(Instant.now())
                .lastMessageAt(Instant.now()).build();
    }

    private SendChatMessageCommand cmd(String content, String clientId) {
        return new SendChatMessageCommand(ROOM_ID, BUYER, ChatMessageType.TEXT, content, clientId);
    }

    @Test
    @DisplayName("정상 송신 시 새 메시지 결과를 반환하고 chatRoom.touch 저장")
    void send_success() {
        // Given
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(chatMessagePort.findByClientMessageId(ROOM_ID, BUYER, "uuid"))
                .willReturn(Optional.empty());
        given(chatMessagePort.nextSeq(ROOM_ID)).willReturn(5L);
        ChatMessage saved = ChatMessage.builder()
                .id(100L).chatRoomId(ROOM_ID).senderId(BUYER).seq(5)
                .messageType(ChatMessageType.TEXT).content("hi")
                .status(ChatMessageStatus.ACTIVE).sentAt(Instant.now()).build();
        given(chatMessagePort.save(any(ChatMessage.class))).willReturn(saved);

        // When
        SendChatMessageResult result = service.send(cmd("hi", "uuid"));

        // Then
        assertThat(result.chatMessageId()).isEqualTo(100L);
        assertThat(result.seq()).isEqualTo(5L);
    }

    @Test
    @DisplayName("messageType이 TEXT가 아니면 InvalidChatMessageException")
    void send_nonText_throws() {
        SendChatMessageCommand image = new SendChatMessageCommand(
                ROOM_ID, BUYER, ChatMessageType.IMAGE, "hi", null);

        assertThatThrownBy(() -> service.send(image))
                .isInstanceOf(InvalidChatMessageException.class);
    }

    @Test
    @DisplayName("채팅방이 없으면 NotFoundChatRoomException")
    void send_roomNotFound_throws() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.empty());
        SendChatMessageCommand command = cmd("hi", null);

        assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(NotFoundChatRoomException.class);
    }

    @Test
    @DisplayName("참여자가 아니면 ForbiddenChatRoomAccessException")
    void send_notParticipant_throws() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        SendChatMessageCommand bySomeoneElse = new SendChatMessageCommand(
                ROOM_ID, 999L, ChatMessageType.TEXT, "hi", null);

        assertThatThrownBy(() -> service.send(bySomeoneElse))
                .isInstanceOf(ForbiddenChatRoomAccessException.class);
    }

    @Test
    @DisplayName("CLOSED 채팅방이면 ChatRoomClosedException")
    void send_closed_throws() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(closedRoom()));
        SendChatMessageCommand command = cmd("hi", null);

        assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(ChatRoomClosedException.class);
    }

    @Test
    @DisplayName("clientMessageId 중복이면 DuplicateClientMessageIdException")
    void send_duplicateClientId_throws() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        ChatMessage existing = ChatMessage.builder()
                .id(50L).chatRoomId(ROOM_ID).senderId(BUYER).seq(3)
                .messageType(ChatMessageType.TEXT).content("prev")
                .status(ChatMessageStatus.ACTIVE).sentAt(Instant.now()).build();
        given(chatMessagePort.findByClientMessageId(ROOM_ID, BUYER, "dup"))
                .willReturn(Optional.of(existing));
        SendChatMessageCommand command = cmd("hi", "dup");

        assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(DuplicateClientMessageIdException.class)
                .satisfies(e -> {
                    DuplicateClientMessageIdException ex = (DuplicateClientMessageIdException) e;
                    assertThat(ex.getExistingMessageId()).isEqualTo(50L);
                    assertThat(ex.getExistingSeq()).isEqualTo(3L);
                });
    }

    @Test
    @DisplayName("2,000자 초과 메시지는 ChatMessageTooLongException (도메인 검증)")
    void send_tooLong_throws() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(chatMessagePort.nextSeq(ROOM_ID)).willReturn(1L);
        String big = "가".repeat(2001);
        SendChatMessageCommand command = cmd(big, null);

        assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(ChatMessageTooLongException.class);
    }

    @Test
    @DisplayName("seq UNIQUE 충돌 시 재시도하여 다음 seq로 저장 성공")
    void send_seqConflict_retries() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(chatMessagePort.nextSeq(anyLong())).willReturn(5L, 6L);
        ChatMessage saved = ChatMessage.builder()
                .id(101L).chatRoomId(ROOM_ID).senderId(BUYER).seq(6)
                .messageType(ChatMessageType.TEXT).content("hi")
                .status(ChatMessageStatus.ACTIVE).sentAt(Instant.now()).build();
        given(chatMessagePort.save(any(ChatMessage.class)))
                .willThrow(new DataIntegrityViolationException("seq race"))
                .willReturn(saved);

        SendChatMessageResult result = service.send(cmd("hi", null));

        assertThat(result.seq()).isEqualTo(6L);
    }

    @Test
    @DisplayName("seq UNIQUE 충돌 3회 연속이면 마지막 예외 전파")
    void send_seqConflictExhausted_throws() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom()));
        given(chatMessagePort.nextSeq(anyLong())).willReturn(5L, 6L, 7L);
        given(chatMessagePort.save(any(ChatMessage.class)))
                .willThrow(new DataIntegrityViolationException("race"));
        SendChatMessageCommand command = cmd("hi", null);

        assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
