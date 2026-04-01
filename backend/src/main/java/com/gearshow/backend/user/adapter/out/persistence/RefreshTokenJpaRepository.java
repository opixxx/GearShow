package com.gearshow.backend.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Refresh Token JPA 저장소.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    /**
     * 토큰 문자열로 Refresh Token을 조회한다.
     *
     * @param token 토큰 문자열
     * @return Refresh Token Optional
     */
    Optional<RefreshTokenJpaEntity> findByToken(String token);

    /**
     * 사용자 ID로 Refresh Token을 삭제한다.
     *
     * @param userId 사용자 ID
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.userId = :userId")
    void deleteByUserId(Long userId);
}
