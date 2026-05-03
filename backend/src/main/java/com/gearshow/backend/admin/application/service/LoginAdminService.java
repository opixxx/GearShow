package com.gearshow.backend.admin.application.service;

import com.gearshow.backend.admin.application.dto.LoginAdminCommand;
import com.gearshow.backend.admin.application.dto.LoginAdminResult;
import com.gearshow.backend.admin.application.port.in.LoginAdminUseCase;
import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.application.port.out.AdminTokenIssuer;
import com.gearshow.backend.admin.application.port.out.PasswordEncoderPort;
import com.gearshow.backend.admin.domain.exception.InvalidCredentialsException;
import com.gearshow.backend.admin.domain.model.Admin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인 유스케이스 구현체.
 *
 * <p>미존재 email 과 비밀번호 불일치를 동일한 {@link InvalidCredentialsException} 으로 통합하여
 * user enumeration 공격을 차단한다 (OWASP Authentication 권고). 내부 로그에서는 두 케이스를 구분해
 * 기록할 수 있으나 응답에는 동일 코드/메시지가 반환된다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAdminService implements LoginAdminUseCase {

    private final AdminPort adminPort;
    private final PasswordEncoderPort passwordEncoder;
    private final AdminTokenIssuer tokenIssuer;

    @Override
    @Transactional(readOnly = true)
    public LoginAdminResult login(LoginAdminCommand command) {
        Admin admin = adminPort.findByEmail(command.email())
                .orElseThrow(() -> {
                    log.info("[admin-login] 실패 — 미존재 email (email={})", command.email());
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(command.password(), admin.getPasswordHash())) {
            log.info("[admin-login] 실패 — 비밀번호 불일치 (adminId={})", admin.getId());
            throw new InvalidCredentialsException();
        }

        return tokenIssuer.issue(admin.getId());
    }
}
