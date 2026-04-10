package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 처리된 메시지 이력 JPA 엔티티.
 *
 * <p>{@code (message_id, domain)} 복합 UNIQUE 제약으로 멱등성을 보장한다.
 * 컬럼 길이는 UUID(36자) / ULID(26자) / enum 이름 기준으로 최소화하여
 * 인덱스 키 크기를 줄이고 INSERT 성능을 높인다.</p>
 */
@Entity
@Table(
        name = "processed_message",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_message_message_id_domain",
                columnNames = {"message_id", "domain"}
        ),
        indexes = {
                @Index(name = "idx_processed_message_processed_at", columnList = "processed_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "processed_message_id")
    private Long id;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "domain", nullable = false, length = 50)
    private String domain;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    private ProcessedMessageJpaEntity(String messageId, String domain, Instant processedAt) {
        this.messageId = messageId;
        this.domain = domain;
        this.processedAt = processedAt;
    }

    /**
     * 새 처리 이력을 생성한다. 처리 시각은 호출 시점으로 자동 설정된다.
     */
    public static ProcessedMessageJpaEntity create(String messageId, String domain) {
        return new ProcessedMessageJpaEntity(messageId, domain, Instant.now());
    }
}
