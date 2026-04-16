package com.gearshow.backend.chat.adapter.in.web.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 메시지 송신 요청 (api-spec §8-5).
 *
 * <p>{@code clientMessageId}는 선택 값이지만 전달되는 경우 공백 문자열은 멱등성 키로
 * 유효하지 않으므로 {@code @Pattern}으로 차단한다. null은 그대로 허용.</p>
 */
public record SendChatMessageRequest(
        @NotNull(message = "messageType은 필수입니다")
        ChatMessageType messageType,

        @NotBlank(message = "content는 비어 있을 수 없습니다")
        @Size(max = 2_000, message = "메시지는 2,000자 이하여야 합니다")
        String content,

        @Size(max = 64, message = "clientMessageId는 64자 이하여야 합니다")
        @Pattern(regexp = "\\S+", message = "clientMessageId는 공백만으로 구성될 수 없습니다")
        String clientMessageId
) {
}
