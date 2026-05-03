package com.gearshow.backend.admin.application.service;

import com.gearshow.backend.admin.application.dto.ChangeAdminPasswordCommand;
import com.gearshow.backend.admin.application.port.in.ChangeAdminPasswordUseCase;
import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.application.port.out.PasswordEncoderPort;
import com.gearshow.backend.admin.domain.exception.InvalidCredentialsException;
import com.gearshow.backend.admin.domain.exception.SameAsCurrentPasswordException;
import com.gearshow.backend.admin.domain.model.Admin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 비밀번호 변경 유스케이스 구현체.
 *
 * <p>인증된 admin 본인의 비밀번호를 회전한다. user enumeration 정책 일관성을 위해
 * adminId 미존재와 currentPassword 불일치를 동일한 {@link InvalidCredentialsException} 으로 처리한다
 * (현실적으로 인증된 토큰이 통과한 후라 미존재는 거의 발생하지 않지만 안전한 방어).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeAdminPasswordService implements ChangeAdminPasswordUseCase {

    private final AdminPort adminPort;
    private final PasswordEncoderPort passwordEncoder;

    @Override
    @Transactional
    public void change(ChangeAdminPasswordCommand command) {
        Admin admin = adminPort.findById(command.adminId())
                .orElseThrow(() -> {
                    log.warn("[admin-password-change] 실패 — 미존재 adminId={}", command.adminId());
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(command.currentPassword(), admin.getPasswordHash())) {
            log.info("[admin-password-change] 실패 — currentPassword 불일치 (adminId={})", admin.getId());
            throw new InvalidCredentialsException();
        }

        if (passwordEncoder.matches(command.newPassword(), admin.getPasswordHash())) {
            log.info("[admin-password-change] 실패 — 새 비밀번호가 현재와 동일 (adminId={})", admin.getId());
            throw new SameAsCurrentPasswordException();
        }

        Admin updated = admin.changePassword(passwordEncoder.encode(command.newPassword()));
        adminPort.save(updated);
        log.info("[admin-password-change] 비밀번호 변경 완료 (adminId={})", admin.getId());
    }
}
