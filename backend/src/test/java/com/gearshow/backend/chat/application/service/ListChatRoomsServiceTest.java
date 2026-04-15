package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult;
import com.gearshow.backend.chat.application.dto.ChatRoomListProjection;
import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.application.port.out.ShowcaseReadPort;
import com.gearshow.backend.chat.application.port.out.UserReadPort;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
import com.gearshow.backend.common.dto.PageInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListChatRoomsServiceTest {

    private static final Long USER_ID = 20L;

    @InjectMocks
    private ListChatRoomsService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ShowcaseReadPort showcaseReadPort;
    @Mock private UserReadPort userReadPort;

    private ChatRoomListProjection row(long roomId, long sellerId, long buyerId,
                                       Long lastMsgId, ChatMessageType type, String content) {
        Instant t = Instant.parse("2026-04-15T10:00:00Z");
        return new ChatRoomListProjection(
                roomId, 42L, sellerId, buyerId, ChatRoomStatus.ACTIVE,
                t, t, lastMsgId, type, content, t, 0L);
    }

    @Test
    @DisplayName("첫 페이지 조회 시 peer 프로필과 showcase 요약이 합쳐진다")
    void list_firstPage_assemblesPeerAndShowcase() {
        // Given: 조회한 user(20)는 buyer, peer는 seller(10)
        given(chatRoomPort.findByParticipantFirstPage(USER_ID, 5)).willReturn(List.of(
                row(1L, 10L, USER_ID, 99L, ChatMessageType.TEXT, "hi")));
        given(userReadPort.getProfiles(anyList())).willReturn(Map.of(
                10L, new UserProfile(10L, "seller", "url")));
        given(showcaseReadPort.getSummaries(anyList())).willReturn(Map.of(
                42L, new ShowcaseSummary(42L, 10L, "Boots", "thumb.jpg", true)));

        // When
        PageInfo<ChatRoomListItemResult> page = service.list(USER_ID, null, 5);

        // Then
        assertThat(page.data()).hasSize(1);
        ChatRoomListItemResult item = page.data().get(0);
        assertThat(item.peer().userId()).isEqualTo(10L);
        assertThat(item.peer().nickname()).isEqualTo("seller");
        assertThat(item.showcaseTitle()).isEqualTo("Boots");
        assertThat(item.lastMessage().content()).isEqualTo("hi");
    }

    @Test
    @DisplayName("lastMessageId가 null이면 lastMessage 응답이 null")
    void list_noMessages_lastMessageNull() {
        given(chatRoomPort.findByParticipantFirstPage(USER_ID, 5)).willReturn(List.of(
                row(1L, 10L, USER_ID, null, null, null)));
        given(userReadPort.getProfiles(anyList())).willReturn(Map.of(
                10L, new UserProfile(10L, "seller", null)));
        given(showcaseReadPort.getSummaries(anyList())).willReturn(Map.of(
                42L, new ShowcaseSummary(42L, 10L, "B", null, true)));

        PageInfo<ChatRoomListItemResult> page = service.list(USER_ID, null, 5);

        assertThat(page.data().get(0).lastMessage()).isNull();
    }

    @Test
    @DisplayName("로그인 유저가 seller일 때 peer는 buyer로 계산된다")
    void list_userIsSeller_peerIsBuyer() {
        given(chatRoomPort.findByParticipantFirstPage(USER_ID, 5)).willReturn(List.of(
                row(1L, USER_ID, 30L, 99L, ChatMessageType.TEXT, "hi")));
        given(userReadPort.getProfiles(anyList())).willReturn(Map.of(
                30L, new UserProfile(30L, "buyer", null)));
        given(showcaseReadPort.getSummaries(anyList())).willReturn(Map.of(
                42L, new ShowcaseSummary(42L, USER_ID, "B", null, true)));

        PageInfo<ChatRoomListItemResult> page = service.list(USER_ID, null, 5);

        assertThat(page.data().get(0).peer().userId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("빈 결과면 외부 BC 호출 없이 빈 페이지 반환")
    void list_empty_skipsExternal() {
        given(chatRoomPort.findByParticipantFirstPage(USER_ID, 5)).willReturn(List.of());

        PageInfo<ChatRoomListItemResult> page = service.list(USER_ID, null, 5);

        assertThat(page.data()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }
}
