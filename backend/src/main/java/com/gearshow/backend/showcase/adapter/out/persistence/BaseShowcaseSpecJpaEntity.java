package com.gearshow.backend.showcase.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 스펙 JPA 엔티티의 공통 필드를 정의하는 상위 클래스.
 * 각 카테고리별 스펙 엔티티(BootsSpec, UniformSpec 등)가 상속한다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseShowcaseSpecJpaEntity {

    @Column(name = "showcase_id", nullable = false, unique = true)
    private Long showcaseId;

    @Column(name = "extra_spec_json", columnDefinition = "json")
    private String extraSpecJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaseShowcaseSpecJpaEntity(Long showcaseId, String extraSpecJson,
                                         Instant createdAt, Instant updatedAt) {
        this.showcaseId = showcaseId;
        this.extraSpecJson = extraSpecJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
