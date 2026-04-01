package com.gearshow.backend.user.application.service;

import com.gearshow.backend.support.TestOAuthConfig;
import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.RefreshTokenCommand;
import com.gearshow.backend.user.application.exception.InvalidTokenException;
import com.gearshow.backend.user.application.port.in.LoginUseCase;
import com.gearshow.backend.user.application.port.in.RefreshTokenUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
@Transactional
class RefreshTokenServiceIntegrationTest {

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;

    @Test
    @DisplayName("유효한 Refresh Token으로 새 토큰을 발급받는다")
    void refresh_withValidToken_returnsNewTokens() {
        // Given
        LoginResult loginResult = loginUseCase.login(new LoginCommand("kakao", "valid-code"));

        // When
        LoginResult refreshResult = refreshTokenUseCase.refresh(
                new RefreshTokenCommand(loginResult.refreshToken()));

        // Then
        assertThat(refreshResult.accessToken()).isNotBlank();
        assertThat(refreshResult.refreshToken()).isNotBlank();
        assertThat(refreshResult.tokenType()).isEqualTo("Bearer");
        assertThat(refreshResult.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("존재하지 않는 Refresh Token으로 갱신하면 예외가 발생한다")
    void refresh_withInvalidToken_throwsException() {
        // Given
        RefreshTokenCommand command = new RefreshTokenCommand("nonexistent-token");

        // When & Then
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(command))
                .isInstanceOf(InvalidTokenException.class);
    }
}
