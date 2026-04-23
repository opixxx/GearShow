package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API 경계 멱등성 키 JPA 엔티티.
 *
 * <p>클라이언트가 {@code Idempotency-Key} 헤더로 보낸 UUID 를 {@code idempotency_key} 컬럼에 저장하고,
 * 같은 key 재도달 시 캐싱된 응답을 반환한다. 상태 전이는 {@link Status#IN_PROGRESS} → {@link Status#DONE}.</p>
 *
 * <p><b>설계 근거</b>: ADR-011 (3D 파이프라인 다층 멱등성) — ① API 계층 방어.</p>
 *
 * <p><b>인덱스</b>: {@code idx_idempotency_key_expires_at} — 만료 레코드 주기 정리 스케줄러용.</p>
 */
@Entity
@Table(
        name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_key_value",
                columnNames = "idempotency_key"
        ),
        indexes = @Index(
                name = "idx_idempotency_key_expires_at",
                columnList = "expires_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "JSON")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    private IdempotencyKeyJpaEntity(String idempotencyKey, Long userId, Status status,
                                    Instant createdAt, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 새 멱등성 키를 {@link Status#IN_PROGRESS} 상태로 생성한다.
     *
     * @param key        클라이언트가 전달한 UUID
     * @param userId     요청 사용자
     * @param expiresAt  만료 시각 (Cleanup 대상)
     */
    public static IdempotencyKeyJpaEntity createInProgress(String key, Long userId, Instant expiresAt) {
        return new IdempotencyKeyJpaEntity(key, userId, Status.IN_PROGRESS, Instant.now(), expiresAt);
    }

    /**
     * 처리 완료 시 HTTP 응답을 캐싱하고 {@link Status#DONE} 으로 전이한다.
     * 재도달한 요청은 이 응답을 그대로 반환받는다.
     */
    public void markDone(Integer httpStatus, String responseBody) {
        this.status = Status.DONE;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public boolean isDone() {
        return this.status == Status.DONE;
    }

    public enum Status {
        IN_PROGRESS,
        DONE
    }
}
