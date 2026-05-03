package com.gearshow.backend.admin.domain.model;

import com.gearshow.backend.admin.domain.exception.InvalidAdminException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 관리자 계정 도메인 모델.
 *
 * <p>일반 사용자({@code User}) 도메인이 OAuth identity provider 에 email 신뢰를 위임하는 것과 달리,
 * 관리자는 OAuth 외부의 내부 운영 계정이므로 GearShow 가 직접 email 을 보유한다.
 * email 은 관리자 식별자이며, 일반 사용자 식별과는 무관하다 (테이블 분리).</p>
 *
 * <p>비밀번호는 평문으로 도메인이 알지 못한다. {@code passwordHash} (BCrypt 등) 만 보유하며,
 * 검증은 application 계층의 {@code PasswordEncoderPort.matches(raw, hash)} 가 수행한다.</p>
 */
@Getter
public class Admin {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private Admin(Long id, String email, String passwordHash,
                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 신규 관리자 계정을 생성한다.
     *
     * @param email        식별자 이메일 (blank 금지)
     * @param passwordHash 비밀번호 해시 (blank 금지). 평문은 절대 도메인에 들어오지 않는다.
     * @return 생성된 관리자
     */
    public static Admin create(String email, String passwordHash) {
        validate(email, passwordHash);
        Instant now = Instant.now();
        return Admin.builder()
                .email(email)
                .passwordHash(passwordHash)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 비밀번호를 변경하여 새 인스턴스를 반환한다 (불변 객체 패턴).
     *
     * <p>평문은 절대 도메인에 들어오지 않는다. application 계층의 PasswordEncoder 로
     * 해시한 결과를 받아 보유한다.</p>
     *
     * @param newPasswordHash 새 비밀번호 해시 (blank 금지)
     * @return 새 hash 와 갱신된 updatedAt 을 가진 새 Admin 인스턴스 (id/email/createdAt 동일)
     */
    public Admin changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new InvalidAdminException();
        }
        return Admin.builder()
                .id(this.id)
                .email(this.email)
                .passwordHash(newPasswordHash)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    private static void validate(String email, String passwordHash) {
        if (email == null || email.isBlank()
                || passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidAdminException();
        }
    }
}
