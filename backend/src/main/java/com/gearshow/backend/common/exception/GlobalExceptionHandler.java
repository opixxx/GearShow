package com.gearshow.backend.common.exception;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.showcase.application.exception.DailyLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

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
     * 3D 모델 일일 quota 초과 — HTTP 429 + {@code Retry-After} 헤더 (KST 다음 자정까지 남은 초).
     *
     * <p>Spring MVC {@code @ExceptionHandler} 매칭은 가장 구체적인 예외 타입을 우선하므로
     * 본 핸들러가 {@link CustomException} 핸들러보다 먼저 매칭된다.</p>
     */
    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleDailyLimitExceeded(
            DailyLimitExceededException e) {
        long retryAfterSeconds = Math.max(1L,
                e.getResetAt().getEpochSecond() - Instant.now().getEpochSecond());
        log.warn("3D 모델 일일 quota 초과: limit={}, resetAt={}, retryAfterSec={}",
                e.getLimit(), e.getResetAt(), retryAfterSeconds);
        return ResponseEntity
                .status(e.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
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
     * 필수 요청 헤더 누락 예외를 400 으로 처리한다.
     *
     * <p>예: {@code Idempotency-Key} 헤더를 required=true 로 선언한 엔드포인트에서
     * 헤더가 빠진 경우 Spring MVC 가 {@link MissingRequestHeaderException} 을 던진다.</p>
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(
            MissingRequestHeaderException e) {
        ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_HEADER;
        String message = errorCode.getMessage() + ": " + e.getHeaderName();
        log.warn("필수 헤더 누락: header={}", e.getHeaderName());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getStatus(), errorCode.name(), message));
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
