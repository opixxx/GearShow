package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 JPA 엔티티.
 *
 * <p><b>인덱스</b>:</p>
 * <ul>
 *   <li>{@code idx_showcase_owner_content_created}: 같은 사용자가 짧은 시간 창(예: 10분) 내에
 *       같은 이미지 조합으로 중복 등록하는 것을 application 레벨에서 감지하기 위한 조회 인덱스.
 *       {@code content_hash} 는 SHA-256 hex 64 문자. 실제 중복 판단은 application 에서
 *       {@code created_at > NOW() - 10min} 조건과 함께 수행하며, DB UNIQUE 는 걸지 않는다
 *       (사용자가 정당하게 10분 밖에서 같은 이미지를 재업로드하는 케이스를 허용하기 위함).</li>
 * </ul>
 */
@Entity
@Table(
        name = "showcase",
        indexes = @Index(
                name = "idx_showcase_owner_content_created",
                columnList = "owner_id, content_hash, created_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "catalog_item_id")
    private Long catalogItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    // ADR-024 §D3: 카탈로그 일시 제외에 따라 brand 는 선택 필드. 컬럼은 카탈로그 복귀 시
    // 재활용을 위해 보존하되 NULL 허용. schema.sql 의 멱등 MODIFY 가 운영 DB 도 함께 완화한다.
    @Column(name = "brand")
    private String brand;

    @Column(name = "model_code")
    private String modelCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "user_size")
    private String userSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_grade", nullable = false)
    private ConditionGrade conditionGrade;

    @Column(name = "wear_count")
    private int wearCount;

    @Column(name = "is_for_sale", nullable = false)
    private boolean forSale;

    @Column(name = "primary_image_url")
    private String primaryImageUrl;

    /**
     * 4장 쇼케이스 이미지의 결정적 SHA-256 해시 (hex 64자).
     * 같은 사용자의 짧은 시간 창 내 중복 등록을 application 계층에서 감지할 때 사용한다.
     * 기존 행 보호를 위해 nullable.
     *
     * <p><b>P1-B TODO</b>: 현재 {@link com.gearshow.backend.showcase.domain.model.Showcase}
     * 도메인 모델에는 이 필드가 없어 {@code ShowcaseMapper} 의 왕복 경로에 포함되지 않는다.
     * P1-B 에서 {@code ContentHash} 도메인 VO 를 도입하고 {@code Showcase.create(...)} 시그니처에
     * 추가한 뒤 Mapper 양방향 매핑을 보강해야 한다 (ADR-011 ②).</p>
     */
    @Column(name = "content_hash", length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "has_3d_model", nullable = false)
    private boolean has3dModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "showcase_status", nullable = false)
    private ShowcaseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private ShowcaseJpaEntity(Long id, Long ownerId, Long catalogItemId,
                              Category category, String brand, String modelCode,
                              String title, String description, String userSize,
                              ConditionGrade conditionGrade, int wearCount, boolean forSale,
                              String primaryImageUrl, String contentHash, boolean has3dModel,
                              ShowcaseStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.catalogItemId = catalogItemId;
        this.category = category;
        this.brand = brand;
        this.modelCode = modelCode;
        this.title = title;
        this.description = description;
        this.userSize = userSize;
        this.conditionGrade = conditionGrade;
        this.wearCount = wearCount;
        this.forSale = forSale;
        this.primaryImageUrl = primaryImageUrl;
        this.contentHash = contentHash;
        this.has3dModel = has3dModel;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
