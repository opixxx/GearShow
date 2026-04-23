package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, Long> {

    /**
     * 멱등성 키로 엔티티를 조회한다.
     *
     * <p><b>보안 주의</b>: 이 조회는 소유자 검증을 포함하지 않는다. 호출부(Service)는 반환된 엔티티의
     * {@link IdempotencyKeyJpaEntity#getUserId()} 와 요청 사용자 ID 의 일치를 반드시 검증해야 한다.
     * 검증 누락 시 다른 사용자의 캐싱 응답을 반환하는 IDOR 가능성이 있다.</p>
     */
    Optional<IdempotencyKeyJpaEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키를 {@code IN_PROGRESS} 상태로 INSERT IGNORE 한다.
     *
     * <p>UNIQUE 제약 위반 시 예외 없이 0 행이 반환된다.
     * 이를 통해 Spring 트랜잭션의 rollback-only 플래그 오염을 회피한다
     * (참조: {@link ProcessedMessageJpaRepository#insertIfAbsent}).</p>
     *
     * @return 신규 저장이면 1, 이미 존재하면 0
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO idempotency_key
                (idempotency_key, user_id, status, created_at, expires_at)
            VALUES (:key, :userId, 'IN_PROGRESS', :createdAt, :expiresAt)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("key") String idempotencyKey,
                       @Param("userId") Long userId,
                       @Param("createdAt") Instant createdAt,
                       @Param("expiresAt") Instant expiresAt);
}
