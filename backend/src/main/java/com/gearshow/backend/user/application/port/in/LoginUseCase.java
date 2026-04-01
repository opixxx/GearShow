package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.LoginResult;

/**
 * 소셜 로그인 유스케이스.
 */
public interface LoginUseCase {

    /**
     * 소셜 인가 코드로 로그인한다.
     * 신규 사용자인 경우 자동 회원가입 후 로그인한다.
     *
     * @param command 로그인 커맨드
     * @return 로그인 결과 (JWT 토큰)
     */
    LoginResult login(LoginCommand command);
}
