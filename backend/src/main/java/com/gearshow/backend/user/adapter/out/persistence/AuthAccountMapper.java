package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.domain.model.AuthAccount;
import org.springframework.stereotype.Component;

/**
 * AuthAccount 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class AuthAccountMapper {

    /**
     * 도메인 모델을 JPA 엔티티로 변환한다.
     *
     * @param authAccount 도메인 모델
     * @return JPA 엔티티
     */
    public AuthAccountJpaEntity toJpaEntity(AuthAccount authAccount) {
        return AuthAccountJpaEntity.builder()
                .id(authAccount.getId())
                .userId(authAccount.getUserId())
                .providerType(authAccount.getProviderType())
                .providerUserKey(authAccount.getProviderUserKey())
                .authStatus(authAccount.getAuthStatus())
                .createdAt(authAccount.getCreatedAt())
                .lastLoginAt(authAccount.getLastLoginAt())
                .build();
    }

    /**
     * JPA 엔티티를 도메인 모델로 변환한다.
     *
     * @param entity JPA 엔티티
     * @return 도메인 모델
     */
    public AuthAccount toDomain(AuthAccountJpaEntity entity) {
        return AuthAccount.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .providerType(entity.getProviderType())
                .providerUserKey(entity.getProviderUserKey())
                .authStatus(entity.getAuthStatus())
                .createdAt(entity.getCreatedAt())
                .lastLoginAt(entity.getLastLoginAt())
                .build();
    }
}
