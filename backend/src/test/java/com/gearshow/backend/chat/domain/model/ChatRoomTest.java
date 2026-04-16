package com.gearshow.backend.chat.domain.model;

import com.gearshow.backend.chat.domain.exception.ChatRoomClosedException;
import com.gearshow.backend.chat.domain.exception.ChatRoomOwnShowcaseException;
import com.gearshow.backend.chat.domain.exception.ForbiddenChatRoomAccessException;
import com.gearshow.backend.chat.domain.exception.InvalidChatRoomException;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomTest {

    private static final Long SHOWCASE_ID = 42L;
    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    @Nested
    @DisplayName("open")
    class Open {

        @Test
        @DisplayName("유효한 값으로 개설하면 ACTIVE 상태이고 lastMessageAt은 createdAt과 같다")
        void open_success() {
            ChatRoom room = ChatRoom.open(SHOWCASE_ID, SELLER_ID, BUYER_ID);

            assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
            assertThat(room.getShowcaseId()).isEqualTo(SHOWCASE_ID);
            assertThat(room.getSellerId()).isEqualTo(SELLER_ID);
            assertThat(room.getBuyerId()).isEqualTo(BUYER_ID);
            // 메시지 미발송 상태에서도 정렬 인덱스 활용을 위해 createdAt으로 채워둔다.
            assertThat(room.getLastMessageAt()).isEqualTo(room.getCreatedAt());
        }

        @Test
        @DisplayName("판매자와 구매자가 동일하면 ChatRoomOwnShowcaseException")
        void open_sameUser_throws() {
            assertThatThrownBy(() -> ChatRoom.open(SHOWCASE_ID, SELLER_ID, SELLER_ID))
                    .isInstanceOf(ChatRoomOwnShowcaseException.class);
        }

        @Test
        @DisplayName("필수 값이 null이면 InvalidChatRoomException")
        void open_nullArg_throws() {
            assertThatThrownBy(() -> ChatRoom.open(null, SELLER_ID, BUYER_ID))
                    .isInstanceOf(InvalidChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("validateParticipant")
    class ValidateParticipant {

        @Test
        @DisplayName("판매자 또는 구매자이면 통과")
        void pass_forParticipants() {
            ChatRoom room = ChatRoom.open(SHOWCASE_ID, SELLER_ID, BUYER_ID);

            room.validateParticipant(SELLER_ID);
            room.validateParticipant(BUYER_ID);
        }

        @Test
        @DisplayName("참여자가 아니면 ForbiddenChatRoomAccessException")
        void throw_forNonParticipant() {
            ChatRoom room = ChatRoom.open(SHOWCASE_ID, SELLER_ID, BUYER_ID);

            assertThatThrownBy(() -> room.validateParticipant(999L))
                    .isInstanceOf(ForbiddenChatRoomAccessException.class);
        }
    }

    @Nested
    @DisplayName("validateSendable")
    class ValidateSendable {

        @Test
        @DisplayName("CLOSED 상태면 ChatRoomClosedException")
        void throw_whenClosed() {
            ChatRoom room = ChatRoom.open(SHOWCASE_ID, SELLER_ID, BUYER_ID).close();

            assertThatThrownBy(room::validateSendable)
                    .isInstanceOf(ChatRoomClosedException.class);
        }
    }

    @Nested
    @DisplayName("touch")
    class Touch {

        @Test
        @DisplayName("lastMessageAt을 최신값으로 갱신한다")
        void touch_updatesLastMessageAt() {
            ChatRoom room = ChatRoom.open(SHOWCASE_ID, SELLER_ID, BUYER_ID);
            Instant now = Instant.now();

            ChatRoom touched = room.touch(now);

            assertThat(touched.getLastMessageAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("peerOf")
    class PeerOf {

        @Test
        @DisplayName("판매자 입장에서 peer는 구매자, 구매자 입장에서 peer는 판매자")
        void peerOf_returnsOtherSide() {
            ChatRoom room = ChatRoom.open(SHOWCASE_ID, SELLER_ID, BUYER_ID);

            assertThat(room.peerOf(SELLER_ID)).isEqualTo(BUYER_ID);
            assertThat(room.peerOf(BUYER_ID)).isEqualTo(SELLER_ID);
        }
    }
}
