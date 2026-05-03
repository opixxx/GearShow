package com.gearshow.backend.admin.application.service;

import com.gearshow.backend.admin.application.dto.LoginAdminCommand;
import com.gearshow.backend.admin.application.dto.LoginAdminResult;
import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.application.port.out.AdminTokenIssuer;
import com.gearshow.backend.admin.application.port.out.PasswordEncoderPort;
import com.gearshow.backend.admin.domain.exception.InvalidCredentialsException;
import com.gearshow.backend.admin.domain.model.Admin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link LoginAdminService} 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class LoginAdminServiceTest {

    @Mock
    private AdminPort adminPort;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @Mock
    private AdminTokenIssuer tokenIssuer;

    @InjectMocks
    private LoginAdminService service;

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상 로그인 시 토큰을 발급한다")
        void login_success() throws Exception {
            // Given
            String email = "admin@gearshow.com";
            String rawPassword = "raw-password";
            Admin admin = Admin.create(email, "stored-hash");
            setId(admin, 7L);

            given(adminPort.findByEmail(email)).willReturn(Optional.of(admin));
            given(passwordEncoder.matches(rawPassword, "stored-hash")).willReturn(true);
            given(tokenIssuer.issue(7L)).willReturn(new LoginAdminResult(7L, "access", "refresh"));

            // When
            LoginAdminResult result = service.login(new LoginAdminCommand(email, rawPassword));

            // Then
            assertThat(result.adminId()).isEqualTo(7L);
            assertThat(result.accessToken()).isEqualTo("access");
            assertThat(result.refreshToken()).isEqualTo("refresh");
        }

        @Test
        @DisplayName("email 미존재 시 InvalidCredentialsException (user enumeration 방지)")
        void login_emailNotFound_throwsInvalidCredentials() {
            // Given
            given(adminPort.findByEmail("missing@gearshow.com")).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() ->
                    service.login(new LoginAdminCommand("missing@gearshow.com", "raw")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenIssuer, never()).issue(org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName("비밀번호 불일치 시 InvalidCredentialsException (미존재 email 과 동일 응답)")
        void login_invalidPassword_throwsInvalidCredentials() throws Exception {
            // Given
            Admin admin = Admin.create("admin@gearshow.com", "stored-hash");
            setId(admin, 1L);

            given(adminPort.findByEmail("admin@gearshow.com")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong", "stored-hash")).willReturn(false);

            // When & Then
            assertThatThrownBy(() ->
                    service.login(new LoginAdminCommand("admin@gearshow.com", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenIssuer, never()).issue(org.mockito.ArgumentMatchers.anyLong());
        }
    }

    private static void setId(Admin admin, Long id) throws Exception {
        Field field = Admin.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(admin, id);
    }
}
