package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.ChatMessageResult;
import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.ForbiddenChatRoomAccessException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import com.gearshow.backend.common.dto.PageInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListChatMessagesServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 20L;

    @InjectMocks
    private ListChatMessagesService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ChatMessagePort chatMessagePort;

    private ChatRoom room() {
        return ChatRoom.builder().id(ROOM_ID).showcaseId(42L)
                .sellerId(10L).buyerId(USER_ID).build();
    }

    private ChatMessage msg(long id, long seq, ChatMessageStatus status) {
        return ChatMessage.builder()
                .id(id).chatRoomId(ROOM_ID).senderId(10L).seq(seq)
                .messageType(ChatMessageType.TEXT).content("body-" + id)
                .status(status).sentAt(Instant.parse("2026-04-15T00:00:00Z").plusSeconds(seq))
                .build();
    }

    @Test
    @DisplayName("first page: DESC 조회 후 응답은 ASC, 가장 오래된 메시지가 첫 항목")
    void list_firstPage_returnsAscending() {
        // Given: DESC로 size+1=4건 반환 (id 5,4,3,2)
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findByChatRoomIdFirstPage(ROOM_ID, 3)).willReturn(List.of(
                msg(5, 5, ChatMessageStatus.ACTIVE),
                msg(4, 4, ChatMessageStatus.ACTIVE),
                msg(3, 3, ChatMessageStatus.ACTIVE),
                msg(2, 2, ChatMessageStatus.ACTIVE)));

        // When
        PageInfo<ChatMessageResult> page = service.list(ROOM_ID, USER_ID, null, 3);

        // Then: ASC로 정렬되어 첫 항목 = id 3 (가장 오래된 페이지 내), 마지막 = id 5
        assertThat(page.data()).extracting(ChatMessageResult::chatMessageId)
                .containsExactly(3L, 4L, 5L);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("DELETED 메시지는 본문이 플레이스홀더로 치환된다")
    void list_deletedMessage_maskedAsPlaceholder() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findByChatRoomIdFirstPage(ROOM_ID, 5)).willReturn(List.of(
                msg(1, 1, ChatMessageStatus.DELETED)));

        PageInfo<ChatMessageResult> page = service.list(ROOM_ID, USER_ID, null, 5);

        assertThat(page.data().get(0).content()).isEqualTo(ChatMessage.DELETED_PLACEHOLDER);
        assertThat(page.data().get(0).payloadJson()).isNull();
    }

    @Test
    @DisplayName("before 파라미터가 있으면 findByChatRoomIdBefore 호출")
    void list_withBefore_callsBeforeQuery() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(chatMessagePort.findByChatRoomIdBefore(ROOM_ID, 10L, 5)).willReturn(List.of(
                msg(9, 9, ChatMessageStatus.ACTIVE)));

        PageInfo<ChatMessageResult> page = service.list(ROOM_ID, USER_ID, 10L, 5);

        assertThat(page.data()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("채팅방이 없으면 NotFoundChatRoomException")
    void list_roomNotFound() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(ROOM_ID, USER_ID, null, 5))
                .isInstanceOf(NotFoundChatRoomException.class);
    }

    @Test
    @DisplayName("참여자가 아니면 ForbiddenChatRoomAccessException")
    void list_notParticipant() {
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room()));

        assertThatThrownBy(() -> service.list(ROOM_ID, 999L, null, 5))
                .isInstanceOf(ForbiddenChatRoomAccessException.class);
    }
}
