package com.gearshow.backend.common.dto;

/**
 * 공통 API 응답 형식.
 *
 * @param status  HTTP 상태 코드
 * @param code    에러 코드 (정상 응답 시 null)
 * @param message 응답 메시지
 * @param data    응답 데이터
 */
public record ApiResponse<T>(
        int status,
        String code,
        String message,
        T data
) {

    /**
     * 성공 응답을 생성한다.
     */
    public static <T> ApiResponse<T> of(int status, String message, T data) {
        return new ApiResponse<>(status, null, message, data);
    }

    /**
     * 데이터 없는 성공 응답을 생성한다.
     */
    public static ApiResponse<Void> of(int status, String message) {
        return new ApiResponse<>(status, null, message, null);
    }

    /**
     * 에러 응답을 생성한다.
     */
    public static ApiResponse<Void> error(int status, String code, String message) {
        return new ApiResponse<>(status, code, message, null);
    }
}
