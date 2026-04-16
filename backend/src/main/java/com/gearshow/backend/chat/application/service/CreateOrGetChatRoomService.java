package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.CreateOrGetChatRoomResult;
import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.application.port.in.CreateOrGetChatRoomUseCase;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.application.port.out.ShowcaseReadPort;
import com.gearshow.backend.chat.domain.exception.ChatRoomOwnShowcaseException;
import com.gearshow.backend.chat.domain.exception.ChatRoomShowcaseNotAvailableException;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 생성-또는-조회 유스케이스 구현체 (api-spec §8-3).
 *
 * <p>(showcaseId, buyerId) 유니크 키 기반으로 idempotent하게 동작한다.
 * 동시 생성 경합 시 {@link DataIntegrityViolationException}을 잡아 재조회로 수렴한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CreateOrGetChatRoomService implements CreateOrGetChatRoomUseCase {

    private final ChatRoomPort chatRoomPort;
    private final ShowcaseReadPort showcaseReadPort;

    @Override
    @Transactional
    public CreateOrGetChatRoomResult createOrGet(Long showcaseId, Long buyerId) {
        ShowcaseSummary summary = showcaseReadPort.getSummary(showcaseId);
        if (summary == null || !summary.chatStartable()) {
            throw new ChatRoomShowcaseNotAvailableException();
        }
        if (summary.sellerId().equals(buyerId)) {
            throw new ChatRoomOwnShowcaseException();
        }

        return chatRoomPort.findByShowcaseIdAndBuyerId(showcaseId, buyerId)
                .map(existing -> new CreateOrGetChatRoomResult(existing.getId(), false))
                .orElseGet(() -> createNew(showcaseId, summary.sellerId(), buyerId));
    }

    private CreateOrGetChatRoomResult createNew(Long showcaseId, Long sellerId, Long buyerId) {
        ChatRoom newRoom = ChatRoom.open(showcaseId, sellerId, buyerId);
        try {
            ChatRoom saved = chatRoomPort.save(newRoom);
            return new CreateOrGetChatRoomResult(saved.getId(), true);
        } catch (DataIntegrityViolationException e) {
            // 동시 생성 경합: 다른 트랜잭션이 먼저 insert 했으므로 재조회로 수렴
            return chatRoomPort.findByShowcaseIdAndBuyerId(showcaseId, buyerId)
                    .map(existing -> new CreateOrGetChatRoomResult(existing.getId(), false))
                    .orElseThrow(() -> e);
        }
    }
}
