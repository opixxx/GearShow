package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.application.port.out.AuthAccountPort;
import com.gearshow.backend.user.domain.model.AuthAccount;
import com.gearshow.backend.user.domain.vo.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 소셜 인증 계정 Persistence Adapter.
 * AuthAccountPort를 구현하여 JPA를 통해 인증 계정 데이터를 관리한다.
 */
@Repository
@RequiredArgsConstructor
public class AuthAccountPersistenceAdapter implements AuthAccountPort {

    private final AuthAccountJpaRepository authAccountJpaRepository;
    private final AuthAccountMapper authAccountMapper;

    @Override
    public AuthAccount save(AuthAccount authAccount) {
        AuthAccountJpaEntity jpaEntity = authAccountMapper.toJpaEntity(authAccount);
        AuthAccountJpaEntity saved = authAccountJpaRepository.save(jpaEntity);
        return authAccountMapper.toDomain(saved);
    }

    @Override
    public Optional<AuthAccount> findByProviderTypeAndProviderUserKey(
            ProviderType providerType, String providerUserKey) {
        return authAccountJpaRepository
                .findByProviderTypeAndProviderUserKey(providerType, providerUserKey)
                .map(authAccountMapper::toDomain);
    }
}
