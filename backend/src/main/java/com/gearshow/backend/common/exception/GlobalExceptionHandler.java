package com.gearshow.backend.common.exception;

import com.gearshow.backend.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
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
        log.warn("비즈니스 예외 발생: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.error(e.getStatus(), e.getCode(), e.getMessage()));
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
                .body(ApiResponse.error(400, "INVALID_INPUT", message));
    }

    /**
     * @Validated 제약 위반 예외를 처리한다 (쿼리 파라미터 검증 등).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("잘못된 입력입니다");

        log.warn("제약 조건 위반: message={}", message);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, "INVALID_INPUT", message));
    }

    /**
     * 예상하지 못한 예외를 처리한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("서버 내부 오류 발생", e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다"));
    }
}
