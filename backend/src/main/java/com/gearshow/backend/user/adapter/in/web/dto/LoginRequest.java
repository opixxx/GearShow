package com.gearshow.backend.user.adapter.in.web.dto;

/**
 * 소셜 로그인 요청 DTO.
 *
 * @param authorizationCode 소셜 인가 코드
 * @param accessToken       소셜 SDK에서 받은 액세스 토큰
 */
public record LoginRequest(
        String authorizationCode,
        String accessToken
) {
}
