package com.gearshow.backend.user.application.port.out;

import com.gearshow.backend.user.application.dto.OAuthUserInfo;

/**
 * OAuth 인증 클라이언트 인터페이스.
 * 제공자별 구현체는 adapter 계층에서 작성한다.
 */
public interface OAuthClient {

    /**
     * 인가 코드를 사용하여 소셜 제공자로부터 사용자 정보를 조회한다.
     *
     * @param authorizationCode 인가 코드
     * @return 사용자 정보
     */
    OAuthUserInfo getUserInfo(String authorizationCode);

    /**
     * 액세스 토큰을 사용하여 소셜 제공자로부터 사용자 정보를 조회한다.
     *
     * @param accessToken 액세스 토큰
     * @return 사용자 정보
     */
    default OAuthUserInfo getUserInfoByAccessToken(String accessToken) {
        throw new UnsupportedOperationException("토큰 기반 조회를 지원하지 않습니다.");
    }

    /**
     * 이 클라이언트가 지원하는 제공자 이름을 반환한다.
     *
     * @return 제공자 이름 (kakao, apple)
     */
    String getProvider();
}
