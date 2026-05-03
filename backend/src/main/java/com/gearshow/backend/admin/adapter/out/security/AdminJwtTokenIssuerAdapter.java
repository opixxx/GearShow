package com.gearshow.backend.admin.adapter.out.security;

import com.gearshow.backend.admin.application.dto.LoginAdminResult;
import com.gearshow.backend.admin.application.port.out.AdminTokenIssuer;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 관리자용 JWT 발급 어댑터.
 *
 * <p>기존 {@link JwtTokenProvider} 를 재사용하되 claim 에 {@code type=ADMIN} 을 포함시킨다.</p>
 */
@Component
@RequiredArgsConstructor
public class AdminJwtTokenIssuerAdapter implements AdminTokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginAdminResult issue(Long adminId) {
        String accessToken = jwtTokenProvider.generateAccessToken(adminId, JwtTokenProvider.TYPE_ADMIN);
        String refreshToken = jwtTokenProvider.generateRefreshToken(adminId, JwtTokenProvider.TYPE_ADMIN);
        return new LoginAdminResult(adminId, accessToken, refreshToken);
    }
}
