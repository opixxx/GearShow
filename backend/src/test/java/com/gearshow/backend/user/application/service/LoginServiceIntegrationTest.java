package com.gearshow.backend.user.application.service;

import com.gearshow.backend.support.TestOAuthConfig;
import com.gearshow.backend.user.adapter.out.persistence.UserJpaRepository;
import com.gearshow.backend.user.adapter.out.persistence.AuthAccountJpaRepository;
import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.exception.InvalidAuthCodeException;
import com.gearshow.backend.user.application.exception.UnsupportedProviderException;
import com.gearshow.backend.user.application.port.in.LoginUseCase;
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
class LoginServiceIntegrationTest {

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private AuthAccountJpaRepository authAccountJpaRepository;

    @Test
    @DisplayName("신규 사용자가 카카오 로그인하면 자동 가입 후 토큰이 발급된다")
    void login_newUser_createsAccountAndReturnsToken() {
        // Given
        LoginCommand command = new LoginCommand("kakao", "valid-code");

        // When
        LoginResult result = loginUseCase.login(command);

        // Then
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600);
        assertThat(userJpaRepository.count()).isEqualTo(1);
        assertThat(authAccountJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 사용자가 로그인하면 새 사용자를 생성하지 않고 토큰을 발급한다")
    void login_existingUser_doesNotCreateNewUser() {
        // Given
        loginUseCase.login(new LoginCommand("kakao", "valid-code"));
        long userCountBefore = userJpaRepository.count();

        // When
        LoginResult result = loginUseCase.login(new LoginCommand("kakao", "valid-code"));

        // Then
        assertThat(result.accessToken()).isNotBlank();
        assertThat(userJpaRepository.count()).isEqualTo(userCountBefore);
    }

    @Test
    @DisplayName("유효하지 않은 인가 코드로 로그인하면 예외가 발생한다")
    void login_invalidCode_throwsException() {
        // Given
        LoginCommand command = new LoginCommand("kakao", "invalid-code");

        // When & Then
        assertThatThrownBy(() -> loginUseCase.login(command))
                .isInstanceOf(InvalidAuthCodeException.class);
    }

    @Test
    @DisplayName("지원하지 않는 제공자로 로그인하면 예외가 발생한다")
    void login_unsupportedProvider_throwsException() {
        // Given
        LoginCommand command = new LoginCommand("naver", "valid-code");

        // When & Then
        assertThatThrownBy(() -> loginUseCase.login(command))
                .isInstanceOf(UnsupportedProviderException.class);
    }
}
