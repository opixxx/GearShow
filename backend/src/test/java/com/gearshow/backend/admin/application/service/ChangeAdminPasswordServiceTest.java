package com.gearshow.backend.admin.application.service;

import com.gearshow.backend.admin.application.dto.ChangeAdminPasswordCommand;
import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.application.port.out.PasswordEncoderPort;
import com.gearshow.backend.admin.domain.exception.InvalidCredentialsException;
import com.gearshow.backend.admin.domain.exception.SameAsCurrentPasswordException;
import com.gearshow.backend.admin.domain.model.Admin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ChangeAdminPasswordService} 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ChangeAdminPasswordServiceTest {

    @Mock
    private AdminPort adminPort;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @InjectMocks
    private ChangeAdminPasswordService service;

    @Nested
    @DisplayName("change")
    class Change {

        @Test
        @DisplayName("정상 변경 — 새 hash 로 save 호출, 평문 password 가 도메인에 들어가지 않음")
        void change_success() throws Exception {
            // Given
            Admin admin = adminWithId(7L, "stored-hash");
            given(adminPort.findById(7L)).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("current-pw", "stored-hash")).willReturn(true);
            given(passwordEncoder.matches("new-pw", "stored-hash")).willReturn(false);
            given(passwordEncoder.encode("new-pw")).willReturn("new-hash");
            given(adminPort.save(any(Admin.class))).willAnswer(inv -> inv.getArgument(0));

            // When
            service.change(new ChangeAdminPasswordCommand(7L, "current-pw", "new-pw"));

            // Then
            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminPort).save(captor.capture());
            Admin saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(7L);
            assertThat(saved.getPasswordHash()).isEqualTo("new-hash");
        }

        @Test
        @DisplayName("adminId 미존재 시 InvalidCredentialsException (user enumeration 정책 일관)")
        void change_adminNotFound_throwsInvalidCredentials() {
            // Given
            given(adminPort.findById(99L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() ->
                    service.change(new ChangeAdminPasswordCommand(99L, "current", "new")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(adminPort, never()).save(any());
        }

        @Test
        @DisplayName("currentPassword 불일치 시 InvalidCredentialsException")
        void change_wrongCurrentPassword_throwsInvalidCredentials() throws Exception {
            // Given
            Admin admin = adminWithId(1L, "stored-hash");
            given(adminPort.findById(1L)).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong", "stored-hash")).willReturn(false);

            // When & Then
            assertThatThrownBy(() ->
                    service.change(new ChangeAdminPasswordCommand(1L, "wrong", "new-pw")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(adminPort, never()).save(any());
        }

        @Test
        @DisplayName("새 비밀번호가 현재와 동일하면 SameAsCurrentPasswordException")
        void change_sameAsCurrent_throws() throws Exception {
            // Given
            Admin admin = adminWithId(1L, "stored-hash");
            given(adminPort.findById(1L)).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("same-pw", "stored-hash")).willReturn(true);

            // When & Then
            assertThatThrownBy(() ->
                    service.change(new ChangeAdminPasswordCommand(1L, "same-pw", "same-pw")))
                    .isInstanceOf(SameAsCurrentPasswordException.class);

            verify(adminPort, never()).save(any());
        }
    }

    private static Admin adminWithId(Long id, String passwordHash) throws Exception {
        Admin admin = Admin.create("admin@gearshow.com", passwordHash);
        Field field = Admin.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(admin, id);
        return admin;
    }
}
