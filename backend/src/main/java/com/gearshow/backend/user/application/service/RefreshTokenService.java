package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.RefreshTokenCommand;
import com.gearshow.backend.user.application.exception.InvalidTokenException;
import com.gearshow.backend.user.application.port.in.RefreshTokenUseCase;
import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 토큰 갱신 유스케이스 구현체.
 * Refresh Token을 검증하고 새로운 토큰 쌍을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final JwtTokenProvider jwtTokenProvider;

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

        return generateNewTokens(userId);
    }

    /**
     * 기존 Refresh Token을 삭제하고 새로운 토큰 쌍을 발급한다.
     */
    private LoginResult generateNewTokens(Long userId) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        refreshTokenPort.deleteByUserId(userId);
        refreshTokenPort.save(userId, refreshToken,
                LocalDateTime.now().plusDays(14));

        return new LoginResult(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }
}
