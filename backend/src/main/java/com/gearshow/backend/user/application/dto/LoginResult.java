package com.gearshow.backend.user.application.dto;

/**
 * 소셜 로그인 결과.
 *
 * @param accessToken  액세스 토큰
 * @param refreshToken 리프레시 토큰
 * @param tokenType    토큰 타입
 * @param expiresIn    만료 시간(초)
 */
public record LoginResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
