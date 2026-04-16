package com.gearshow.backend.chat.adapter.in.web;

import com.gearshow.backend.chat.adapter.in.web.dto.MarkReadRequest;
import com.gearshow.backend.chat.application.port.in.MarkChatRoomReadUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅방 읽음 처리 API (api-spec §8-7).
 */
@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/read")
@RequiredArgsConstructor
@Validated
public class ChatReadController {

    private final MarkChatRoomReadUseCase markChatRoomReadUseCase;

    @PostMapping
    public ApiResponse<Void> mark(
            Authentication authentication,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody MarkReadRequest request) {

        Long userId = (Long) authentication.getPrincipal();
        markChatRoomReadUseCase.mark(chatRoomId, userId, request.lastReadMessageId());
        return ApiResponse.of(200, "읽음 처리 성공");
    }
}
