package com.gearshow.backend.user.application.port.in;

/**
 * 로그아웃 유스케이스.
 */
public interface LogoutUseCase {

    /**
     * 현재 사용자의 Refresh Token을 무효화하여 로그아웃한다.
     *
     * @param userId 사용자 ID
     */
    void logout(Long userId);
}
