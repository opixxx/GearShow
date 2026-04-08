package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.RefreshTokenCommand;
import com.gearshow.backend.user.application.exception.InvalidTokenException;
import com.gearshow.backend.user.application.port.in.RefreshTokenUseCase;
import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import com.gearshow.backend.user.application.port.out.TokenIssuer;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 갱신 유스케이스 구현체.
 * Refresh Token을 검증하고 새로운 토큰 쌍을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenIssuer tokenIssuer;

    @Override
    @Transactional
    public LoginResult refresh(RefreshTokenCommand command) {
        Long userId = refreshTokenPort
                .findUserIdByToken(command.refreshToken())
                .orElseThrow(InvalidTokenException::new);

        if (!jwtTokenProvider.validateToken(command.refreshToken())) {
            refreshTokenPort.deleteByUserId(userId);
            throw new InvalidTokenException();
        }

        return tokenIssuer.issue(userId);
    }
}
