package com.gearshow.backend.user.domain.repository;

import com.gearshow.backend.user.domain.model.AuthAccount;
import com.gearshow.backend.user.domain.vo.ProviderType;

import java.util.List;
import java.util.Optional;

/**
 * 소셜 인증 계정 도메인 저장소 인터페이스.
 */
public interface AuthAccountRepository {

    /**
     * 인증 계정을 저장한다.
     *
     * @param authAccount 저장할 인증 계정
     * @return 저장된 인증 계정
     */
    AuthAccount save(AuthAccount authAccount);

    /**
     * 제공자 유형과 제공자 사용자 키로 인증 계정을 조회한다.
     *
     * @param providerType    인증 제공자 유형
     * @param providerUserKey 제공자 측 사용자 고유 키
     * @return 인증 계정 Optional
     */
    Optional<AuthAccount> findByProviderTypeAndProviderUserKey(
            ProviderType providerType, String providerUserKey);

    /**
     * 사용자 ID로 연동된 인증 계정 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 인증 계정 목록
     */
    List<AuthAccount> findByUserId(Long userId);
}
