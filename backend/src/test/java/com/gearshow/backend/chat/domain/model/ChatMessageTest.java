package com.gearshow.backend.chat.domain.model;

import com.gearshow.backend.chat.domain.exception.ChatMessageNotOwnerException;
import com.gearshow.backend.chat.domain.exception.ChatMessageSystemUndeletableException;
import com.gearshow.backend.chat.domain.exception.ChatMessageTooLongException;
import com.gearshow.backend.chat.domain.exception.InvalidChatMessageException;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageTest {

    private static final Long ROOM_ID = 1L;
    private static final Long SENDER_ID = 10L;

    @Nested
    @DisplayName("text")
    class Text {

        @Test
        @DisplayName("정상적인 본문으로 생성하면 ACTIVE TEXT 메시지이다")
        void text_success() {
            ChatMessage m = ChatMessage.text(ROOM_ID, SENDER_ID, 1, "안녕하세요", "uuid-1");

            assertThat(m.getMessageType()).isEqualTo(ChatMessageType.TEXT);
            assertThat(m.getStatus()).isEqualTo(ChatMessageStatus.ACTIVE);
            assertThat(m.getContent()).isEqualTo("안녕하세요");
            assertThat(m.getClientMessageId()).isEqualTo("uuid-1");
            assertThat(m.getSenderId()).isEqualTo(SENDER_ID);
        }

        @Test
        @DisplayName("본문이 2,000자를 초과하면 ChatMessageTooLongException")
        void text_tooLong_throws() {
            String content = "가".repeat(ChatMessage.MAX_CONTENT_LENGTH + 1);

            assertThatThrownBy(() -> ChatMessage.text(ROOM_ID, SENDER_ID, 1, content, null))
                    .isInstanceOf(ChatMessageTooLongException.class);
        }

        @Test
        @DisplayName("본문이 빈 문자열이면 InvalidChatMessageException")
        void text_blank_throws() {
            assertThatThrownBy(() -> ChatMessage.text(ROOM_ID, SENDER_ID, 1, "   ", null))
                    .isInstanceOf(InvalidChatMessageException.class);
        }

        @Test
        @DisplayName("senderId가 null이면 InvalidChatMessageException")
        void text_noSender_throws() {
            assertThatThrownBy(() -> ChatMessage.text(ROOM_ID, null, 1, "hi", null))
                    .isInstanceOf(InvalidChatMessageException.class);
        }
    }

    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("본인이 삭제하면 DELETED 상태로 전이된다")
        void softDelete_success() {
            ChatMessage m = ChatMessage.text(ROOM_ID, SENDER_ID, 1, "안녕", null);

            ChatMessage deleted = m.softDelete(SENDER_ID);

            assertThat(deleted.getStatus()).isEqualTo(ChatMessageStatus.DELETED);
            assertThat(deleted.getContent()).isEqualTo("안녕");
        }

        @Test
        @DisplayName("본인이 아니면 ChatMessageNotOwnerException")
        void softDelete_notOwner_throws() {
            ChatMessage m = ChatMessage.text(ROOM_ID, SENDER_ID, 1, "안녕", null);

            assertThatThrownBy(() -> m.softDelete(999L))
                    .isInstanceOf(ChatMessageNotOwnerException.class);
        }

        @Test
        @DisplayName("시스템 메시지는 삭제 불가")
        void softDelete_system_throws() {
            ChatMessage system = ChatMessage.builder()
                    .chatRoomId(ROOM_ID)
                    .senderId(null)
                    .seq(1)
                    .messageType(ChatMessageType.SYSTEM_TICKET_ISSUED)
                    .content("티켓 발급")
                    .status(ChatMessageStatus.ACTIVE)
                    .build();

            assertThatThrownBy(() -> system.softDelete(SENDER_ID))
                    .isInstanceOf(ChatMessageNotOwnerException.class);
        }

        @Test
        @DisplayName("본인 시스템 메시지(비정상 케이스)도 시스템 타입이면 삭제 불가")
        void softDelete_systemWithSender_throws() {
            ChatMessage system = ChatMessage.builder()
                    .chatRoomId(ROOM_ID)
                    .senderId(SENDER_ID)
                    .seq(1)
                    .messageType(ChatMessageType.SYSTEM_TICKET_ISSUED)
                    .content("티켓 발급")
                    .status(ChatMessageStatus.ACTIVE)
                    .build();

            assertThatThrownBy(() -> system.softDelete(SENDER_ID))
                    .isInstanceOf(ChatMessageSystemUndeletableException.class);
        }
    }
}
