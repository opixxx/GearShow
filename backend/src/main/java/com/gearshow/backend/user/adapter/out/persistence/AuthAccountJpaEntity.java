package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.domain.vo.AuthStatus;
import com.gearshow.backend.user.domain.vo.ProviderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 소셜 인증 계정 JPA 엔티티.
 */
@Entity
@Table(name = "auth_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_account_provider",
                columnNames = {"provider_type", "provider_user_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_account_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Column(name = "provider_user_key", nullable = false)
    private String providerUserKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_status", nullable = false)
    private AuthStatus authStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Builder
    private AuthAccountJpaEntity(Long id, Long userId, ProviderType providerType,
                                 String providerUserKey, AuthStatus authStatus,
                                 Instant createdAt, Instant lastLoginAt) {
        this.id = id;
        this.userId = userId;
        this.providerType = providerType;
        this.providerUserKey = providerUserKey;
        this.authStatus = authStatus;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }
}
