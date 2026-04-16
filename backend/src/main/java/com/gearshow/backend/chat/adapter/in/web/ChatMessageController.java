package com.gearshow.backend.chat.adapter.in.web;

import com.gearshow.backend.chat.adapter.in.web.dto.ChatMessageResponse;
import com.gearshow.backend.chat.adapter.in.web.dto.SendChatMessageRequest;
import com.gearshow.backend.chat.adapter.in.web.dto.SendChatMessageResponse;
import com.gearshow.backend.chat.application.dto.ChatMessageResult;
import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.dto.SendChatMessageResult;
import com.gearshow.backend.chat.application.port.in.DeleteChatMessageUseCase;
import com.gearshow.backend.chat.application.port.in.ListChatMessagesUseCase;
import com.gearshow.backend.chat.application.port.in.SendChatMessageUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 메시지 API (api-spec §8-4, §8-5, §8-8).
 */
@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/messages")
@RequiredArgsConstructor
@Validated
public class ChatMessageController {

    private final ListChatMessagesUseCase listChatMessagesUseCase;
    private final SendChatMessageUseCase sendChatMessageUseCase;
    private final DeleteChatMessageUseCase deleteChatMessageUseCase;

    @GetMapping
    public ApiResponse<PageInfo<ChatMessageResponse>> list(
            Authentication authentication,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 200, message = "페이지 크기는 200 이하여야 합니다")
            int size) {

        Long userId = (Long) authentication.getPrincipal();
        PageInfo<ChatMessageResult> raw =
                listChatMessagesUseCase.list(chatRoomId, userId, before, size);
        List<ChatMessageResponse> items = raw.data().stream()
                .map(ChatMessageResponse::from)
                .toList();
        PageInfo<ChatMessageResponse> page =
                new PageInfo<>(raw.pageToken(), items, raw.size(), raw.hasNext());
        return ApiResponse.of(200, "메시지 목록 조회 성공", page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SendChatMessageResponse> send(
            Authentication authentication,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody SendChatMessageRequest request) {

        Long userId = (Long) authentication.getPrincipal();
        SendChatMessageResult result = sendChatMessageUseCase.send(new SendChatMessageCommand(
                chatRoomId,
                userId,
                request.messageType(),
                request.content(),
                request.clientMessageId()));
        return ApiResponse.of(201, "메시지 전송 성공", SendChatMessageResponse.from(result));
    }

    @DeleteMapping("/{chatMessageId}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable Long chatRoomId,
            @PathVariable Long chatMessageId) {

        Long userId = (Long) authentication.getPrincipal();
        deleteChatMessageUseCase.delete(chatRoomId, chatMessageId, userId);
        return ApiResponse.of(200, "메시지 삭제 성공");
    }
}
