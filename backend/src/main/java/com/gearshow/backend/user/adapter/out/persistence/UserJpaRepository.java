package com.gearshow.backend.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 JPA 저장소.
 */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    /**
     * 닉네임으로 사용자를 조회한다.
     *
     * @param nickname 닉네임
     * @return 사용자 JPA 엔티티 Optional
     */
    Optional<UserJpaEntity> findByNickname(String nickname);

    /**
     * 닉네임 중복 여부를 확인한다.
     *
     * @param nickname 닉네임
     * @return 중복 여부
     */
    boolean existsByNickname(String nickname);
}
