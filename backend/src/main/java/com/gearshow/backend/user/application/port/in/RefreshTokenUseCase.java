package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.RefreshTokenCommand;

/**
 * 토큰 갱신 유스케이스.
 */
public interface RefreshTokenUseCase {

    /**
     * Refresh Token으로 새로운 토큰을 발급한다.
     *
     * @param command 토큰 갱신 커맨드
     * @return 갱신된 토큰 결과
     */
    LoginResult refresh(RefreshTokenCommand command);
}
