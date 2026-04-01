package com.gearshow.backend.user.domain.model;

import com.gearshow.backend.user.domain.exception.InvalidAuthAccountException;
import com.gearshow.backend.user.domain.vo.AuthStatus;
import com.gearshow.backend.user.domain.vo.ProviderType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소셜 인증 계정 도메인 엔티티.
 *
 * <p>USER Aggregate에 종속되며, 하나의 사용자는 여러 소셜 계정을 연동할 수 있다.</p>
 */
@Getter
public class AuthAccount {

    private final Long id;
    private final Long userId;
    private final ProviderType providerType;
    private final String providerUserKey;
    private final AuthStatus authStatus;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastLoginAt;

    @Builder
    private AuthAccount(Long id, Long userId, ProviderType providerType,
                        String providerUserKey, AuthStatus authStatus,
                        LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        this.id = id;
        this.userId = userId;
        this.providerType = providerType;
        this.providerUserKey = providerUserKey;
        this.authStatus = authStatus;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * 새로운 소셜 인증 계정을 생성한다.
     * 최초 상태는 LINKED이다.
     *
     * @param userId          사용자 ID
     * @param providerType    인증 제공자 유형
     * @param providerUserKey 제공자 측 사용자 고유 키
     * @return 생성된 인증 계정
     */
    public static AuthAccount create(Long userId, ProviderType providerType,
                                     String providerUserKey) {
        validate(userId, providerType, providerUserKey);

        LocalDateTime now = LocalDateTime.now();
        return AuthAccount.builder()
                .userId(userId)
                .providerType(providerType)
                .providerUserKey(providerUserKey)
                .authStatus(AuthStatus.LINKED)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
    }

    /**
     * 로그인 시각을 갱신한다.
     *
     * @return 로그인 시각이 갱신된 인증 계정
     */
    public AuthAccount updateLastLogin() {
        return AuthAccount.builder()
                .id(this.id)
                .userId(this.userId)
                .providerType(this.providerType)
                .providerUserKey(this.providerUserKey)
                .authStatus(this.authStatus)
                .createdAt(this.createdAt)
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    /**
     * 계정 연동을 해제한다.
     *
     * @return 연동 해제된 인증 계정
     */
    public AuthAccount unlink() {
        return AuthAccount.builder()
                .id(this.id)
                .userId(this.userId)
                .providerType(this.providerType)
                .providerUserKey(this.providerUserKey)
                .authStatus(AuthStatus.UNLINKED)
                .createdAt(this.createdAt)
                .lastLoginAt(this.lastLoginAt)
                .build();
    }

    private static void validate(Long userId, ProviderType providerType,
                                 String providerUserKey) {
        if (userId == null || providerType == null
                || providerUserKey == null || providerUserKey.isBlank()) {
            throw new InvalidAuthAccountException();
        }
    }
}
