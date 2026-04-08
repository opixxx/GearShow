package com.gearshow.backend.user.application.port.out;

import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.OAuthUserInfo;

/**
 * 소셜 로그인 커맨드로부터 사용자 정보를 조회하는 아웃바운드 포트.
 * 제공자 라우팅과 인증 방식(인가 코드 / 액세스 토큰) 분기를 캡슐화한다.
 */
public interface OAuthUserInfoResolver {

    /**
     * 로그인 커맨드에 담긴 제공자와 인증 정보로 소셜 사용자 정보를 조회한다.
     *
     * @param command 로그인 커맨드
     * @return 소셜 사용자 정보
     */
    OAuthUserInfo resolve(LoginCommand command);
}
