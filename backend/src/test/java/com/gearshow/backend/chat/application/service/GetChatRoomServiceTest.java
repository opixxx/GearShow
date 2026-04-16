package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.ChatRoomDetailResult;
import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.application.port.out.UserReadPort;
import com.gearshow.backend.chat.domain.exception.ForbiddenChatRoomAccessException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetChatRoomServiceTest {

    @InjectMocks
    private GetChatRoomService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private UserReadPort userReadPort;

    @Test
    @DisplayName("참여자가 조회하면 상세 정보가 반환된다")
    void get_byParticipant_returnsDetail() {
        // Given
        ChatRoom room = ChatRoom.builder()
                .id(1L).showcaseId(42L).sellerId(10L).buyerId(20L).build();
        given(chatRoomPort.findById(1L)).willReturn(Optional.of(room));
        given(userReadPort.getProfiles(anyList())).willReturn(Map.of(
                10L, new UserProfile(10L, "seller", "https://s.jpg"),
                20L, new UserProfile(20L, "buyer", null)));

        // When
        ChatRoomDetailResult result = service.get(1L, 20L);

        // Then
        assertThat(result.chatRoomId()).isEqualTo(1L);
        assertThat(result.seller().nickname()).isEqualTo("seller");
        assertThat(result.buyer().nickname()).isEqualTo("buyer");
        assertThat(result.buyer().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("채팅방이 없으면 NotFoundChatRoomException")
    void get_notFound_throws() {
        given(chatRoomPort.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L, 20L))
                .isInstanceOf(NotFoundChatRoomException.class);
    }

    @Test
    @DisplayName("참여자가 아닌 사용자가 조회하면 ForbiddenChatRoomAccessException")
    void get_notParticipant_throws() {
        ChatRoom room = ChatRoom.builder()
                .id(1L).showcaseId(42L).sellerId(10L).buyerId(20L).build();
        given(chatRoomPort.findById(1L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> service.get(1L, 999L))
                .isInstanceOf(ForbiddenChatRoomAccessException.class);
    }

    @Test
    @DisplayName("프로필 조회 결과가 없는 유저는 nickname null로 반환")
    void get_missingProfile_fallbackNull() {
        ChatRoom room = ChatRoom.builder()
                .id(1L).showcaseId(42L).sellerId(10L).buyerId(20L).build();
        given(chatRoomPort.findById(1L)).willReturn(Optional.of(room));
        given(userReadPort.getProfiles(List.of(10L, 20L))).willReturn(Map.of());

        ChatRoomDetailResult result = service.get(1L, 10L);

        assertThat(result.seller().nickname()).isNull();
        assertThat(result.buyer().nickname()).isNull();
    }
}
