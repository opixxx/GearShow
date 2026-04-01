package com.gearshow.backend.user.application.dto;

/**
 * 토큰 갱신 요청 커맨드.
 *
 * @param refreshToken 리프레시 토큰
 */
public record RefreshTokenCommand(
        String refreshToken
) {
}
