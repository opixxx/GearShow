package com.gearshow.backend.admin.application.port.in;

import com.gearshow.backend.admin.application.dto.LoginAdminCommand;
import com.gearshow.backend.admin.application.dto.LoginAdminResult;

/**
 * 관리자 로그인 유스케이스.
 */
public interface LoginAdminUseCase {

    /**
     * email + password 로 관리자를 인증하고 JWT 를 발급한다.
     *
     * @param command 로그인 커맨드
     * @return Access/Refresh 토큰
     */
    LoginAdminResult login(LoginAdminCommand command);
}
