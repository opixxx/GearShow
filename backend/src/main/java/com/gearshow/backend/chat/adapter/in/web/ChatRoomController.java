package com.gearshow.backend.chat.adapter.in.web;

import com.gearshow.backend.chat.adapter.in.web.dto.ChatRoomDetailResponse;
import com.gearshow.backend.chat.adapter.in.web.dto.ChatRoomIdResponse;
import com.gearshow.backend.chat.adapter.in.web.dto.ChatRoomListItemResponse;
import com.gearshow.backend.chat.adapter.in.web.dto.CreateChatRoomRequest;
import com.gearshow.backend.chat.application.dto.ChatRoomDetailResult;
import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult;
import com.gearshow.backend.chat.application.dto.CreateOrGetChatRoomResult;
import com.gearshow.backend.chat.application.port.in.CreateOrGetChatRoomUseCase;
import com.gearshow.backend.chat.application.port.in.GetChatRoomUseCase;
import com.gearshow.backend.chat.application.port.in.ListChatRoomsUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅방 API (api-spec §8-1 ~ §8-3).
 */
@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
@Validated
public class ChatRoomController {

    private final ListChatRoomsUseCase listChatRoomsUseCase;
    private final GetChatRoomUseCase getChatRoomUseCase;
    private final CreateOrGetChatRoomUseCase createOrGetChatRoomUseCase;

    @GetMapping
    public ApiResponse<PageInfo<ChatRoomListItemResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            int size) {

        Long userId = (Long) authentication.getPrincipal();
        PageInfo<ChatRoomListItemResult> raw = listChatRoomsUseCase.list(userId, pageToken, size);
        List<ChatRoomListItemResponse> items = raw.data().stream()
                .map(ChatRoomListItemResponse::from)
                .toList();
        PageInfo<ChatRoomListItemResponse> page =
                new PageInfo<>(raw.pageToken(), items, raw.size(), raw.hasNext());

        return ApiResponse.of(200, "채팅방 목록 조회 성공", page);
    }

    @GetMapping("/{chatRoomId}")
    public ApiResponse<ChatRoomDetailResponse> get(
            Authentication authentication,
            @PathVariable Long chatRoomId) {

        Long userId = (Long) authentication.getPrincipal();
        ChatRoomDetailResult detail = getChatRoomUseCase.get(chatRoomId, userId);
        return ApiResponse.of(200, "채팅방 조회 성공", ChatRoomDetailResponse.from(detail));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoomIdResponse>> createOrGet(
            Authentication authentication,
            @Valid @RequestBody CreateChatRoomRequest request) {

        Long buyerId = (Long) authentication.getPrincipal();
        CreateOrGetChatRoomResult result =
                createOrGetChatRoomUseCase.createOrGet(request.showcaseId(), buyerId);

        ChatRoomIdResponse body = new ChatRoomIdResponse(result.chatRoomId());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.of(201, "채팅방 생성 성공", body));
        }
        return ResponseEntity.ok(ApiResponse.of(200, "채팅방 조회 성공", body));
    }
}
