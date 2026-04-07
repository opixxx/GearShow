package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.LoginResult;

/**
 * 개발 환경 전용 로그인 유스케이스.
 * OAuth 인증 없이 테스트 사용자로 JWT를 발급한다.
 */
public interface DevLoginUseCase {

    /**
     * 개발용 테스트 사용자로 로그인한다.
     * 사용자가 없으면 자동 생성한다.
     *
     * @return JWT 토큰
     */
    LoginResult devLogin();
}
