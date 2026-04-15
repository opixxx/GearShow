package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.CreateOrGetChatRoomResult;
import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.application.port.out.ShowcaseReadPort;
import com.gearshow.backend.chat.domain.exception.ChatRoomOwnShowcaseException;
import com.gearshow.backend.chat.domain.exception.ChatRoomShowcaseNotAvailableException;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CreateOrGetChatRoomServiceTest {

    private static final Long SHOWCASE_ID = 42L;
    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    @InjectMocks
    private CreateOrGetChatRoomService service;

    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ShowcaseReadPort showcaseReadPort;

    @Test
    @DisplayName("기존 채팅방이 있으면 created=false로 반환한다")
    void returnsExisting_whenAlreadyOpen() {
        // Given
        given(showcaseReadPort.getSummary(SHOWCASE_ID))
                .willReturn(new ShowcaseSummary(SHOWCASE_ID, SELLER_ID, "t", null, true));
        ChatRoom existing = ChatRoom.builder()
                .id(99L).showcaseId(SHOWCASE_ID).sellerId(SELLER_ID).buyerId(BUYER_ID).build();
        given(chatRoomPort.findByShowcaseIdAndBuyerId(SHOWCASE_ID, BUYER_ID))
                .willReturn(Optional.of(existing));

        // When
        CreateOrGetChatRoomResult result = service.createOrGet(SHOWCASE_ID, BUYER_ID);

        // Then
        assertThat(result.chatRoomId()).isEqualTo(99L);
        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("기존 채팅방이 없으면 신규 생성하고 created=true 반환")
    void createsNew_whenNotExists() {
        // Given
        given(showcaseReadPort.getSummary(SHOWCASE_ID))
                .willReturn(new ShowcaseSummary(SHOWCASE_ID, SELLER_ID, "t", null, true));
        given(chatRoomPort.findByShowcaseIdAndBuyerId(SHOWCASE_ID, BUYER_ID))
                .willReturn(Optional.empty());
        given(chatRoomPort.save(any(ChatRoom.class))).willAnswer(inv -> {
            ChatRoom incoming = inv.getArgument(0);
            return ChatRoom.builder().id(100L).showcaseId(incoming.getShowcaseId())
                    .sellerId(incoming.getSellerId()).buyerId(incoming.getBuyerId()).build();
        });

        // When
        CreateOrGetChatRoomResult result = service.createOrGet(SHOWCASE_ID, BUYER_ID);

        // Then
        assertThat(result.chatRoomId()).isEqualTo(100L);
        assertThat(result.created()).isTrue();
    }

    @Test
    @DisplayName("자기 쇼케이스에 채팅방을 생성하면 ChatRoomOwnShowcaseException")
    void throws_whenOwnShowcase() {
        given(showcaseReadPort.getSummary(SHOWCASE_ID))
                .willReturn(new ShowcaseSummary(SHOWCASE_ID, BUYER_ID, "t", null, true));

        assertThatThrownBy(() -> service.createOrGet(SHOWCASE_ID, BUYER_ID))
                .isInstanceOf(ChatRoomOwnShowcaseException.class);
    }

    @Test
    @DisplayName("쇼케이스가 chatStartable=false이면 ChatRoomShowcaseNotAvailableException")
    void throws_whenShowcaseNotAvailable() {
        given(showcaseReadPort.getSummary(SHOWCASE_ID))
                .willReturn(new ShowcaseSummary(SHOWCASE_ID, SELLER_ID, "t", null, false));

        assertThatThrownBy(() -> service.createOrGet(SHOWCASE_ID, BUYER_ID))
                .isInstanceOf(ChatRoomShowcaseNotAvailableException.class);
    }

    @Test
    @DisplayName("쇼케이스가 null이면 ChatRoomShowcaseNotAvailableException")
    void throws_whenShowcaseNull() {
        given(showcaseReadPort.getSummary(SHOWCASE_ID)).willReturn(null);

        assertThatThrownBy(() -> service.createOrGet(SHOWCASE_ID, BUYER_ID))
                .isInstanceOf(ChatRoomShowcaseNotAvailableException.class);
    }

    @Test
    @DisplayName("동시 생성 경합 시 DataIntegrityViolationException 잡고 재조회로 수렴")
    void recovers_whenDataIntegrityViolation() {
        given(showcaseReadPort.getSummary(SHOWCASE_ID))
                .willReturn(new ShowcaseSummary(SHOWCASE_ID, SELLER_ID, "t", null, true));
        // 첫 조회: 없음 → save 시도 → 충돌 → 재조회: 다른 트랜잭션이 만든 것 발견
        ChatRoom raceWinner = ChatRoom.builder()
                .id(101L).showcaseId(SHOWCASE_ID).sellerId(SELLER_ID).buyerId(BUYER_ID).build();
        given(chatRoomPort.findByShowcaseIdAndBuyerId(SHOWCASE_ID, BUYER_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(raceWinner));
        given(chatRoomPort.save(any(ChatRoom.class)))
                .willThrow(new DataIntegrityViolationException("race"));

        CreateOrGetChatRoomResult result = service.createOrGet(SHOWCASE_ID, BUYER_ID);

        assertThat(result.chatRoomId()).isEqualTo(101L);
        assertThat(result.created()).isFalse();
    }
}
