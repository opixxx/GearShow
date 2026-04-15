package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult;
import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult.LastMessage;
import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult.Peer;
import com.gearshow.backend.chat.application.dto.ChatRoomListProjection;
import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.chat.application.port.in.ListChatRoomsUseCase;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.application.port.out.ShowcaseReadPort;
import com.gearshow.backend.chat.application.port.out.UserReadPort;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 참여 중인 채팅방 목록 조회 유스케이스 구현체 (api-spec §8-1).
 *
 * <p>채팅방·마지막 메시지·unread count는 Port 단일 쿼리로 받고,
 * 상대방 프로필·쇼케이스 요약은 배치 조회로 합성해 N+1을 회피한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ListChatRoomsService implements ListChatRoomsUseCase {

    private final ChatRoomPort chatRoomPort;
    private final ShowcaseReadPort showcaseReadPort;
    private final UserReadPort userReadPort;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<ChatRoomListItemResult> list(Long userId, String pageToken, int size) {
        List<ChatRoomListProjection> rows;
        if (pageToken == null) {
            rows = chatRoomPort.findByParticipantFirstPage(userId, size);
        } else {
            Pair<Instant, Long> cursor = PageTokenUtil.decode(pageToken, Instant.class, Long.class);
            rows = chatRoomPort.findByParticipantWithCursor(
                    userId, cursor.getLeft(), cursor.getRight(), size);
        }

        Set<Long> peerIds = rows.stream()
                .map(r -> peerOf(userId, r))
                .collect(Collectors.toSet());
        Set<Long> showcaseIds = rows.stream()
                .map(ChatRoomListProjection::showcaseId)
                .collect(Collectors.toSet());

        Map<Long, UserProfile> profiles = peerIds.isEmpty()
                ? Map.of() : userReadPort.getProfiles(List.copyOf(peerIds));
        Map<Long, ShowcaseSummary> showcases = showcaseIds.isEmpty()
                ? Map.of() : showcaseReadPort.getSummaries(List.copyOf(showcaseIds));

        List<ChatRoomListItemResult> items = rows.stream()
                .map(r -> toItem(userId, r, profiles, showcases))
                .toList();

        return PageInfo.of(items, size,
                ChatRoomListItemResult::lastActivityAt,
                ChatRoomListItemResult::chatRoomId);
    }

    private ChatRoomListItemResult toItem(Long userId,
                                          ChatRoomListProjection r,
                                          Map<Long, UserProfile> profiles,
                                          Map<Long, ShowcaseSummary> showcases) {
        Long peerId = peerOf(userId, r);
        UserProfile peer = profiles.get(peerId);
        ShowcaseSummary showcase = showcases.get(r.showcaseId());

        LastMessage last = r.lastMessageId() == null
                ? null
                : new LastMessage(r.lastMessageContent(), r.lastMessageType(), r.lastMessageSentAt());

        // lastMessageAt은 NOT NULL (미발송 시 createdAt 값으로 저장) → DB 정렬 키와 1:1 대응.
        Instant activity = r.lastMessageAt();

        return new ChatRoomListItemResult(
                r.chatRoomId(),
                r.showcaseId(),
                showcase != null ? showcase.title() : null,
                showcase != null ? showcase.thumbnailUrl() : null,
                new Peer(peerId,
                        peer != null ? peer.nickname() : null,
                        peer != null ? peer.profileImageUrl() : null),
                last,
                r.unreadCount(),
                r.status(),
                activity
        );
    }

    private Long peerOf(Long userId, ChatRoomListProjection r) {
        return userId.equals(r.sellerId()) ? r.buyerId() : r.sellerId();
    }
}
