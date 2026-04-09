package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseStatusTransitionException;
import com.gearshow.backend.showcase.domain.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 쇼케이스 도메인 엔티티 (Aggregate Root).
 *
 * <p>사용자가 소유한 장비의 상태, 착용 후기, 3D 모델 등을 관리한다.</p>
 */
@Getter
public class Showcase {

    private final Long id;
    private final Long ownerId;
    private final Long catalogItemId;
    private final Category category;
    private final String brand;
    private final String modelCode;
    private final String title;
    private final String description;
    private final String userSize;
    private final ConditionGrade conditionGrade;
    private final int wearCount;
    private final boolean forSale;
    private final String primaryImageUrl;
    private final boolean has3dModel;
    private final ShowcaseStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private Showcase(Long id, Long ownerId, Long catalogItemId,
                     Category category, String brand, String modelCode,
                     String title, String description, String userSize,
                     ConditionGrade conditionGrade, int wearCount, boolean forSale,
                     String primaryImageUrl, boolean has3dModel,
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
        this.has3dModel = has3dModel;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 쇼케이스를 생성한다.
     * 최초 상태는 ACTIVE이다.
     * 카탈로그 아이템 연결은 선택사항이며, category와 brand는 필수이다.
     *
     * @param ownerId        소유자 ID
     * @param catalogItemId  카탈로그 아이템 ID (선택, null 허용)
     * @param category       카테고리
     * @param brand          브랜드명
     * @param modelCode      모델 코드 (선택)
     * @param title          제목
     * @param description    설명
     * @param userSize       사용자 사이즈
     * @param conditionGrade 상태 등급
     * @param wearCount      착용 횟수
     * @param forSale        판매 여부
     * @return 생성된 쇼케이스
     */
    public static Showcase create(Long ownerId, Long catalogItemId,
                                  Category category, String brand, String modelCode,
                                  String title, String description,
                                  String userSize, ConditionGrade conditionGrade,
                                  int wearCount, boolean forSale,
                                  String primaryImageUrl) {
        validate(ownerId, category, brand, title, conditionGrade);

        Instant now = Instant.now();
        return Showcase.builder()
                .ownerId(ownerId)
                .catalogItemId(catalogItemId)
                .category(category)
                .brand(brand)
                .modelCode(modelCode)
                .title(title)
                .description(description)
                .userSize(userSize)
                .conditionGrade(conditionGrade)
                .wearCount(wearCount)
                .forSale(forSale)
                .primaryImageUrl(primaryImageUrl)
                .has3dModel(false)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 소유자인지 검증한다.
     * 소유자가 아니면 예외를 발생시킨다.
     *
     * @param userId 검증할 사용자 ID
     */
    public void validateOwner(Long userId) {
        if (!this.ownerId.equals(userId)) {
            throw new NotOwnerShowcaseException();
        }
    }

    /**
     * 쇼케이스 정보를 수정한다.
     * null이 아닌 필드만 변경된다 (Partial Update).
     *
     * @param title          변경할 제목 (null이면 유지)
     * @param description    변경할 설명 (null이면 유지)
     * @param userSize       변경할 사용자 사이즈 (null이면 유지)
     * @param conditionGrade 변경할 상태 등급 (null이면 유지)
     * @param wearCount      변경할 착용 횟수 (null이면 유지)
     * @param forSale        변경할 판매 여부 (null이면 유지)
     * @return 수정된 쇼케이스
     */
    public Showcase update(String title, String description, String userSize,
                           ConditionGrade conditionGrade, Integer wearCount, Boolean forSale) {
        if (title != null && title.isBlank()) {
            throw new InvalidShowcaseException();
        }

        return toBuilder()
                .title(title != null ? title : this.title)
                .description(description != null ? description : this.description)
                .userSize(userSize != null ? userSize : this.userSize)
                .conditionGrade(conditionGrade != null ? conditionGrade : this.conditionGrade)
                .wearCount(wearCount != null ? wearCount : this.wearCount)
                .forSale(forSale != null ? forSale : this.forSale)
                .build();
    }

    /**
     * 쇼케이스를 비공개로 전환한다.
     *
     * @return 비공개 처리된 쇼케이스
     */
    public Showcase hide() {
        validateStatusTransition(ShowcaseStatus.HIDDEN);
        return toBuilder()
                .status(ShowcaseStatus.HIDDEN)
                .build();
    }

    /**
     * 쇼케이스를 공개 상태로 전환한다.
     *
     * @return 공개 처리된 쇼케이스
     */
    public Showcase activate() {
        validateStatusTransition(ShowcaseStatus.ACTIVE);
        return toBuilder()
                .status(ShowcaseStatus.ACTIVE)
                .build();
    }

    /**
     * 쇼케이스를 삭제한다 (소프트 삭제).
     *
     * @return 삭제된 쇼케이스
     */
    public Showcase delete() {
        validateStatusTransition(ShowcaseStatus.DELETED);
        return toBuilder()
                .status(ShowcaseStatus.DELETED)
                .build();
    }

    /**
     * 쇼케이스를 판매 완료 처리한다.
     * 판매 완료 시 isForSale은 자동으로 false가 된다.
     *
     * @return 판매 완료된 쇼케이스
     */
    public Showcase markAsSold() {
        validateStatusTransition(ShowcaseStatus.SOLD);
        return toBuilder()
                .status(ShowcaseStatus.SOLD)
                .forSale(false)
                .build();
    }

    /**
     * 판매 여부를 변경한다.
     *
     * @param forSale 판매 여부
     * @return 변경된 쇼케이스
     */
    public Showcase changeForSale(boolean forSale) {
        return toBuilder()
                .forSale(forSale)
                .build();
    }

    /**
     * 대표 이미지 URL을 변경한다.
     *
     * @param primaryImageUrl 새 대표 이미지 URL
     * @return 변경된 쇼케이스
     */
    public Showcase changePrimaryImageUrl(String primaryImageUrl) {
        return toBuilder()
                .primaryImageUrl(primaryImageUrl)
                .build();
    }

    /**
     * 3D 모델 보유 여부를 변경한다.
     *
     * @param has3dModel 3D 모델 보유 여부
     * @return 변경된 쇼케이스
     */
    public Showcase changeHas3dModel(boolean has3dModel) {
        return toBuilder()
                .has3dModel(has3dModel)
                .build();
    }

    private void validateStatusTransition(ShowcaseStatus target) {
        boolean valid = switch (this.status) {
            case ACTIVE -> target == ShowcaseStatus.HIDDEN
                    || target == ShowcaseStatus.DELETED
                    || target == ShowcaseStatus.SOLD;
            case HIDDEN -> target == ShowcaseStatus.ACTIVE
                    || target == ShowcaseStatus.DELETED;
            case SOLD, DELETED -> false;
        };

        if (!valid) {
            throw new InvalidShowcaseStatusTransitionException();
        }
    }

    private Showcase.ShowcaseBuilder toBuilder() {
        return Showcase.builder()
                .id(this.id)
                .ownerId(this.ownerId)
                .catalogItemId(this.catalogItemId)
                .category(this.category)
                .brand(this.brand)
                .modelCode(this.modelCode)
                .title(this.title)
                .description(this.description)
                .userSize(this.userSize)
                .conditionGrade(this.conditionGrade)
                .wearCount(this.wearCount)
                .forSale(this.forSale)
                .primaryImageUrl(this.primaryImageUrl)
                .has3dModel(this.has3dModel)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now());
    }

    /**
     * 쇼케이스 생성 시 필수 필드를 검증한다.
     * category와 brand는 필수, catalogItemId는 선택이다.
     */
    private static void validate(Long ownerId, Category category, String brand,
                                 String title, ConditionGrade conditionGrade) {
        if (ownerId == null || category == null
                || brand == null || brand.isBlank()
                || title == null || title.isBlank()
                || conditionGrade == null) {
            throw new InvalidShowcaseException();
        }
    }
}
