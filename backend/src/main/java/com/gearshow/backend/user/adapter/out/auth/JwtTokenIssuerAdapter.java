package com.gearshow.backend.user.adapter.out.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import com.gearshow.backend.user.application.port.out.TokenIssuer;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

/**
 * JWT 기반 토큰 발급 어댑터.
 * Access Token과 Refresh Token을 생성하고,
 * 기존 Refresh Token을 교체하여 DB에 저장한다.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenIssuerAdapter implements TokenIssuer {

    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(14);

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenPort refreshTokenPort;

    @Override
    public LoginResult issue(Long userId) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        refreshTokenPort.deleteByUserId(userId);
        refreshTokenPort.save(userId, refreshToken,
                Instant.now().plus(REFRESH_TOKEN_VALIDITY));

        return new LoginResult(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }
}
