package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.port.in.LogoutUseCase;
import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그아웃 유스케이스 구현체.
 * 사용자의 Refresh Token을 DB에서 삭제하여 무효화한다.
 */
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenPort refreshTokenPort;

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenPort.deleteByUserId(userId);
    }
}
