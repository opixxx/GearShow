package com.gearshow.backend.user.application.port.out;

import com.gearshow.backend.user.application.dto.LoginResult;

/**
 * 인증 토큰 발급을 추상화하는 아웃바운드 포트.
 * Access Token과 Refresh Token을 생성하고 저장하는 책임을 가진다.
 */
public interface TokenIssuer {

    /**
     * 사용자에게 새로운 토큰 쌍(Access + Refresh)을 발급한다.
     * 기존 Refresh Token은 삭제 후 재발급한다.
     *
     * @param userId 토큰을 발급할 사용자 ID
     * @return 발급된 토큰 정보
     */
    LoginResult issue(Long userId);

    /**
     * 토큰의 유효성을 검증한다.
     *
     * @param token 검증할 토큰
     * @return 유효 여부
     */
    boolean validateToken(String token);
}
