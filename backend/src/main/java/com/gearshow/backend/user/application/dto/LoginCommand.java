package com.gearshow.backend.user.application.dto;

/**
 * 소셜 로그인 요청 커맨드.
 * authorizationCode 또는 accessToken 중 하나는 반드시 값이 있어야 한다.
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

    /**
     * 인가 코드 기반 커맨드를 생성한다.
     */
    public static LoginCommand ofCode(String provider, String authorizationCode) {
        return new LoginCommand(provider, authorizationCode, null);
    }

    /**
     * 액세스 토큰 기반 커맨드를 생성한다.
     */
    public static LoginCommand ofAccessToken(String provider, String accessToken) {
        return new LoginCommand(provider, null, accessToken);
    }

    /**
     * 유효한 인가 코드가 있는지 확인한다.
     */
    public boolean hasAuthorizationCode() {
        return authorizationCode != null && !authorizationCode.isBlank();
    }

    /**
     * 유효한 액세스 토큰이 있는지 확인한다.
     */
    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}
