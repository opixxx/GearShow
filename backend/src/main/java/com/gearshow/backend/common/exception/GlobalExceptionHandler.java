package com.gearshow.backend.common.exception;

import com.gearshow.backend.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * CustomException을 처리한다.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.warn("비즈니스 예외 발생: status={}, message={}", e.getStatus(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.of(e.getStatus(), e.getMessage()));
    }

    /**
     * Bean Validation 예외를 처리한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("잘못된 입력입니다");

        log.warn("유효성 검증 실패: message={}", message);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.of(400, message));
    }

    /**
     * 예상하지 못한 예외를 처리한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("서버 내부 오류 발생", e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.of(500, "서버 내부 오류가 발생했습니다"));
    }
}
