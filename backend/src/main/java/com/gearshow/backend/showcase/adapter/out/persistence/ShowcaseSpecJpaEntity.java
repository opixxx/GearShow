package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.SpecType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 스펙 JPA 엔티티.
 * 카테고리별 스펙을 단일 테이블에서 JSON으로 관리한다.
 */
@Entity
@Table(name = "showcase_spec",
        uniqueConstraints = @UniqueConstraint(columnNames = "showcase_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseSpecJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_spec_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false)
    private Long showcaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_type", nullable = false)
    private SpecType specType;

    @Column(name = "spec_data", columnDefinition = "json", nullable = false)
    private String specData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private ShowcaseSpecJpaEntity(Long id, Long showcaseId, SpecType specType,
                                   String specData, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.specType = specType;
        this.specData = specData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
