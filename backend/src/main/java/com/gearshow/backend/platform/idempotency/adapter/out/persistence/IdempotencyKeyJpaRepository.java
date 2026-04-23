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
     * 멱등성 키를 지정 상태로 INSERT IGNORE 한다.
     *
     * <p>UNIQUE 제약 위반 시 예외 없이 0 행이 반환된다.
     * 이를 통해 Spring 트랜잭션의 rollback-only 플래그 오염을 회피한다
     * (참조: {@link ProcessedMessageJpaRepository#insertIfAbsent}).</p>
     *
     * <p>{@code status} 를 파라미터로 받아 enum 리터럴 하드코딩을 피한다. 호출자는
     * {@link IdempotencyKeyJpaEntity.Status#name()} 을 전달해 컴파일 타임 안전성을 확보한다.</p>
     *
     * @return 신규 저장이면 1, 이미 존재하면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO idempotency_key
                (idempotency_key, user_id, status, created_at, expires_at)
            VALUES (:key, :userId, :status, :createdAt, :expiresAt)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("key") String idempotencyKey,
                       @Param("userId") Long userId,
                       @Param("status") String status,
                       @Param("createdAt") Instant createdAt,
                       @Param("expiresAt") Instant expiresAt);

    /**
     * {@code IN_PROGRESS} 상태의 레코드만 {@code DONE} 으로 전이하고 응답을 캐싱한다.
     *
     * <p>조건부 UPDATE (CAS) 로 동시 markDone 호출 시 Lost Update 를 방지한다.
     * ({@link IdempotencyKeyPersistenceAdapter#markDone})</p>
     *
     * @return 전이 성공 1, 이미 DONE 이거나 레코드 없음 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE idempotency_key
               SET status = :doneStatus,
                   http_status = :httpStatus,
                   response_body = :responseBody
             WHERE idempotency_key = :key
               AND status = :expectedStatus
            """, nativeQuery = true)
    int markDoneIfInProgress(@Param("key") String idempotencyKey,
                             @Param("expectedStatus") String expectedStatus,
                             @Param("doneStatus") String doneStatus,
                             @Param("httpStatus") int httpStatus,
                             @Param("responseBody") String responseBody);

    /**
     * 비즈니스 실패 시 보상 삭제. 같은 키로 재시도 가능하도록 IN_PROGRESS 좀비를 제거한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM idempotency_key WHERE idempotency_key = :key",
            nativeQuery = true)
    int deleteByIdempotencyKey(@Param("key") String idempotencyKey);
}
