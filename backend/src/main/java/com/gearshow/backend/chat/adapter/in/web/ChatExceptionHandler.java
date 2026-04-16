package com.gearshow.backend.chat.adapter.in.web;

import com.gearshow.backend.chat.adapter.in.web.dto.DuplicatedChatMessageResponse;
import com.gearshow.backend.chat.domain.exception.DuplicateClientMessageIdException;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * chat 모듈 전용 예외 핸들러.
 *
 * <p>{@link DuplicateClientMessageIdException}은 409 응답 본문에 기존 메시지 식별자를 함께 실어
 * 클라이언트가 자체적으로 동일 메시지로 수렴할 수 있게 한다 (api-spec §8-5).</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.gearshow.backend.chat")
public class ChatExceptionHandler {

    @ExceptionHandler(DuplicateClientMessageIdException.class)
    public ResponseEntity<ApiResponse<DuplicatedChatMessageResponse>> handleDuplicate(
            DuplicateClientMessageIdException e) {

        log.info("중복 clientMessageId 재전송: existingId={}, seq={}",
                e.getExistingMessageId(), e.getExistingSeq());

        DuplicatedChatMessageResponse body = new DuplicatedChatMessageResponse(
                e.getExistingMessageId(),
                e.getExistingSeq(),
                e.getExistingSentAt());

        ErrorCode code = ErrorCode.DUPLICATE_CLIENT_MESSAGE_ID;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(code.getStatus(), code.name(), code.getMessage(), body));
    }
}
