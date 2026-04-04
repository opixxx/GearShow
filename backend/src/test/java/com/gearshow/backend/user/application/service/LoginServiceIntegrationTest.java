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
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("인가 코드 기반 로그인")
    class AuthorizationCodeLogin {

        @Test
        @DisplayName("신규 사용자가 카카오 로그인하면 자동 가입 후 토큰이 발급된다")
        void login_newUser_createsAccountAndReturnsToken() {
            // Given
            LoginCommand command = new LoginCommand("kakao", "valid-code", null);

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
            loginUseCase.login(new LoginCommand("kakao", "valid-code", null));
            long userCountBefore = userJpaRepository.count();

            // When
            LoginResult result = loginUseCase.login(new LoginCommand("kakao", "valid-code", null));

            // Then
            assertThat(result.accessToken()).isNotBlank();
            assertThat(userJpaRepository.count()).isEqualTo(userCountBefore);
        }

        @Test
        @DisplayName("유효하지 않은 인가 코드로 로그인하면 예외가 발생한다")
        void login_invalidCode_throwsException() {
            // Given
            LoginCommand command = new LoginCommand("kakao", "invalid-code", null);

            // When & Then
            assertThatThrownBy(() -> loginUseCase.login(command))
                    .isInstanceOf(InvalidAuthCodeException.class);
        }
    }

    @Nested
    @DisplayName("액세스 토큰 기반 로그인")
    class AccessTokenLogin {

        @Test
        @DisplayName("유효한 액세스 토큰으로 로그인하면 토큰이 발급된다")
        void login_withAccessToken_returnsToken() {
            // Given
            LoginCommand command = new LoginCommand("kakao", null, "valid-access-token");

            // When
            LoginResult result = loginUseCase.login(command);

            // Then
            assertThat(result.accessToken()).isNotBlank();
            assertThat(result.refreshToken()).isNotBlank();
            assertThat(userJpaRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("액세스 토큰이 인가 코드보다 우선 사용된다")
        void login_withBothTokenAndCode_usesAccessToken() {
            // Given - accessToken과 authorizationCode 둘 다 제공
            LoginCommand command = new LoginCommand("kakao", "valid-code", "valid-access-token");

            // When
            LoginResult result = loginUseCase.login(command);

            // Then - accessToken 경로로 처리되어 정상 토큰 발급
            assertThat(result.accessToken()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionCases {

        @Test
        @DisplayName("지원하지 않는 제공자로 로그인하면 예외가 발생한다")
        void login_unsupportedProvider_throwsException() {
            // Given
            LoginCommand command = new LoginCommand("naver", "valid-code", null);

            // When & Then
            assertThatThrownBy(() -> loginUseCase.login(command))
                    .isInstanceOf(UnsupportedProviderException.class);
        }

        @Test
        @DisplayName("인가 코드와 액세스 토큰이 모두 없으면 예외가 발생한다")
        void login_noCodeAndNoToken_throwsException() {
            // Given
            LoginCommand command = new LoginCommand("kakao", null, null);

            // When & Then
            assertThatThrownBy(() -> loginUseCase.login(command))
                    .isInstanceOf(InvalidAuthCodeException.class);
        }

        @Test
        @DisplayName("인가 코드와 액세스 토큰이 모두 빈 문자열이면 예외가 발생한다")
        void login_emptyCodeAndToken_throwsException() {
            // Given
            LoginCommand command = new LoginCommand("kakao", "  ", "  ");

            // When & Then
            assertThatThrownBy(() -> loginUseCase.login(command))
                    .isInstanceOf(InvalidAuthCodeException.class);
        }
    }

    @Nested
    @DisplayName("닉네임 생성")
    class NicknameGeneration {

        @Test
        @DisplayName("신규 사용자의 닉네임은 '사용자_'로 시작하는 임시 닉네임이다")
        void login_newUser_hasTemporaryNickname() {
            // Given
            LoginCommand command = new LoginCommand("kakao", "valid-code", null);

            // When
            loginUseCase.login(command);

            // Then
            var user = userJpaRepository.findAll().getFirst();
            assertThat(user.getNickname()).startsWith("사용자_");
        }
    }
}
