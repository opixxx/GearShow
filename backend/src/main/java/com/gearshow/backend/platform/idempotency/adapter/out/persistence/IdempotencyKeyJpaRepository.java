package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
