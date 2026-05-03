package com.gearshow.backend.admin.application.service;

import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.application.port.out.PasswordEncoderPort;
import com.gearshow.backend.admin.domain.model.Admin;
import com.gearshow.backend.admin.application.config.AdminBootstrapProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link BootstrapAdminService} 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminServiceTest {

    @Mock
    private AdminPort adminPort;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @Nested
    @DisplayName("bootstrap")
    class Bootstrap {

        @Test
        @DisplayName("email/password 모두 채워져 있고 미존재면 BCrypt 해시 후 INSERT")
        void bootstrap_inserts_whenAllPresentAndNotExists() {
            // Given
            String email = "admin@gearshow.com";
            String rawPassword = "raw-password";
            BootstrapAdminService service = serviceWithProperties(email, rawPassword);

            given(adminPort.existsByEmail(email)).willReturn(false);
            given(passwordEncoder.encode(rawPassword)).willReturn("hashed");
            given(adminPort.save(any(Admin.class))).willAnswer(inv -> inv.getArgument(0));

            // When
            service.bootstrap();

            // Then
            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminPort).save(captor.capture());
            Admin saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo(email);
            assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        }

        @Test
        @DisplayName("이미 같은 email 의 admin 이 존재하면 INSERT 0회")
        void bootstrap_skips_whenAlreadyExists() {
            // Given
            BootstrapAdminService service = serviceWithProperties("admin@gearshow.com", "pw");
            given(adminPort.existsByEmail("admin@gearshow.com")).willReturn(true);

            // When
            service.bootstrap();

            // Then
            verify(adminPort, never()).save(any());
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("email 이 비어있으면 INSERT 0회 (환경변수 미설정)")
        void bootstrap_skips_whenEmailBlank() {
            // Given
            BootstrapAdminService service = serviceWithProperties("", "pw");

            // When
            service.bootstrap();

            // Then
            verify(adminPort, never()).existsByEmail(anyString());
            verify(adminPort, never()).save(any());
        }

        @Test
        @DisplayName("password 가 비어있으면 INSERT 0회 (환경변수 미설정)")
        void bootstrap_skips_whenPasswordBlank() {
            // Given
            BootstrapAdminService service = serviceWithProperties("admin@gearshow.com", "");

            // When
            service.bootstrap();

            // Then
            verify(adminPort, never()).existsByEmail(anyString());
            verify(adminPort, never()).save(any());
        }
    }

    private BootstrapAdminService serviceWithProperties(String email, String password) {
        return new BootstrapAdminService(
                new AdminBootstrapProperties(email, password),
                adminPort,
                passwordEncoder);
    }
}
