package com.gearshow.backend.user.application.dto;

/**
 * 소셜 로그인 요청 커맨드.
 *
 * @param provider          소셜 로그인 제공자 (kakao, apple)
 * @param authorizationCode 인가 코드
 * @param accessToken       SDK에서 획득한 액세스 토큰
 */
public record LoginCommand(
        String provider,
        String authorizationCode,
        String accessToken
) {
}
