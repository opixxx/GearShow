package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.ChatRoomDetailResult;
import com.gearshow.backend.chat.application.dto.ChatRoomDetailResult.ChatParticipant;
import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.chat.application.port.in.GetChatRoomUseCase;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.application.port.out.UserReadPort;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 채팅방 상세 조회 유스케이스 구현체 (api-spec §8-2).
 */
@Service
@RequiredArgsConstructor
public class GetChatRoomService implements GetChatRoomUseCase {

    private final ChatRoomPort chatRoomPort;
    private final UserReadPort userReadPort;

    @Override
    @Transactional(readOnly = true)
    public ChatRoomDetailResult get(Long chatRoomId, Long requesterId) {
        ChatRoom room = chatRoomPort.findById(chatRoomId)
                .orElseThrow(NotFoundChatRoomException::new);
        room.validateParticipant(requesterId);

        Map<Long, UserProfile> profiles = userReadPort.getProfiles(
                List.of(room.getSellerId(), room.getBuyerId()));

        return new ChatRoomDetailResult(
                room.getId(),
                room.getShowcaseId(),
                toParticipant(room.getSellerId(), profiles),
                toParticipant(room.getBuyerId(), profiles),
                room.getStatus(),
                room.getCreatedAt(),
                room.getLastMessageAt()
        );
    }

    private ChatParticipant toParticipant(Long userId, Map<Long, UserProfile> profiles) {
        UserProfile p = profiles.get(userId);
        if (p == null) {
            return new ChatParticipant(userId, null, null);
        }
        return new ChatParticipant(userId, p.nickname(), p.profileImageUrl());
    }
}
