package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.domain.vo.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 소셜 인증 계정 JPA 저장소.
 */
public interface AuthAccountJpaRepository extends JpaRepository<AuthAccountJpaEntity, Long> {

    /**
     * 제공자 유형과 제공자 사용자 키로 인증 계정을 조회한다.
     *
     * @param providerType    인증 제공자 유형
     * @param providerUserKey 제공자 측 사용자 고유 키
     * @return 인증 계정 JPA 엔티티 Optional
     */
    Optional<AuthAccountJpaEntity> findByProviderTypeAndProviderUserKey(
            ProviderType providerType, String providerUserKey);

    /**
     * 사용자 ID로 인증 계정 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 인증 계정 JPA 엔티티 목록
     */
    List<AuthAccountJpaEntity> findByUserId(Long userId);
}
