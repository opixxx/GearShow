package com.gearshow.backend.admin.application.service;

import com.gearshow.backend.admin.application.config.AdminBootstrapProperties;
import com.gearshow.backend.admin.application.port.in.BootstrapAdminUseCase;
import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.application.port.out.PasswordEncoderPort;
import com.gearshow.backend.admin.domain.model.Admin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환경변수 기반 첫 관리자 부트스트랩 서비스.
 *
 * <p>{@link ApplicationStartedEvent} 시점에 {@link AdminBootstrapProperties} 의 email/password 가
 * 채워져 있고 같은 email 이 DB 에 없으면 BCrypt 해시 후 INSERT 한다.</p>
 *
 * <p>운영 배포 시:
 * <ol>
 *   <li>{@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD} 환경변수 주입</li>
 *   <li>애플리케이션 시작 → 부트스트랩 1회 INSERT</li>
 *   <li>첫 로그인 후 비밀번호 변경 (후속 PR)</li>
 *   <li>환경변수 제거 권장</li>
 * </ol>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminService implements BootstrapAdminUseCase {

    private final AdminBootstrapProperties properties;
    private final AdminPort adminPort;
    private final PasswordEncoderPort passwordEncoder;

    /**
     * 첫 관리자 부트스트랩.
     *
     * <p><b>이 메서드는 반드시 {@code public} 으로 유지되어야 한다.</b>
     * {@code @EventListener} + {@code @Transactional} 조합은 Spring AOP 프록시를 통해 호출되며,
     * private/protected 로 가시성을 좁히면 트랜잭션이 소리 없이 무력화된다.</p>
     *
     * <p>다중 인스턴스 동시 부팅 시 {@code existsByEmail} 검사 후 {@code save} 사이의 race window
     * 에서 다른 인스턴스가 먼저 INSERT 하면 UK 위반이 발생한다. 이는 데이터 무결성 측면에서 안전한
     * 충돌(다른 인스턴스가 같은 admin 을 INSERT 한 의미상 동일 결과)이므로
     * {@link DataIntegrityViolationException} 을 catch 하여 graceful 하게 skip 한다.</p>
     */
    @Override
    @EventListener(ApplicationStartedEvent.class)
    @Transactional
    public void bootstrap() {
        String email = properties.email();
        String password = properties.password();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("[admin-bootstrap] 환경변수 미설정 — skip");
            return;
        }

        if (adminPort.existsByEmail(email)) {
            log.info("[admin-bootstrap] 이미 존재함 — skip (email={})", email);
            return;
        }

        Admin admin = Admin.create(email, passwordEncoder.encode(password));
        try {
            Admin saved = adminPort.save(admin);
            log.info("[admin-bootstrap] 첫 관리자 생성 완료 — adminId={}, email={}",
                    saved.getId(), saved.getEmail());
        } catch (DataIntegrityViolationException ex) {
            log.info("[admin-bootstrap] 동시 부트스트랩 충돌 — 다른 인스턴스가 먼저 INSERT 함 (email={})",
                    email);
        }
    }
}
