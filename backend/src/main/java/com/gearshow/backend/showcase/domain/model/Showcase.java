package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseStatusTransitionException;
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
    private final String title;
    private final String description;
    private final String userSize;
    private final ConditionGrade conditionGrade;
    private final int wearCount;
    private final boolean forSale;
    private final ShowcaseStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private Showcase(Long id, Long ownerId, Long catalogItemId, String title,
                     String description, String userSize, ConditionGrade conditionGrade,
                     int wearCount, boolean forSale, ShowcaseStatus status,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.catalogItemId = catalogItemId;
        this.title = title;
        this.description = description;
        this.userSize = userSize;
        this.conditionGrade = conditionGrade;
        this.wearCount = wearCount;
        this.forSale = forSale;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 쇼케이스를 생성한다.
     * 최초 상태는 ACTIVE이며, 판매 여부는 false이다.
     *
     * @param ownerId        소유자 ID
     * @param catalogItemId  카탈로그 아이템 ID
     * @param title          제목
     * @param conditionGrade 상태 등급
     * @return 생성된 쇼케이스
     */
    public static Showcase create(Long ownerId, Long catalogItemId,
                                  String title, ConditionGrade conditionGrade) {
        validate(ownerId, catalogItemId, title, conditionGrade);

        Instant now = Instant.now();
        return Showcase.builder()
                .ownerId(ownerId)
                .catalogItemId(catalogItemId)
                .title(title)
                .conditionGrade(conditionGrade)
                .wearCount(0)
                .forSale(false)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
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
                .title(this.title)
                .description(this.description)
                .userSize(this.userSize)
                .conditionGrade(this.conditionGrade)
                .wearCount(this.wearCount)
                .forSale(this.forSale)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now());
    }

    private static void validate(Long ownerId, Long catalogItemId,
                                 String title, ConditionGrade conditionGrade) {
        if (ownerId == null || catalogItemId == null
                || title == null || title.isBlank()
                || conditionGrade == null) {
            throw new InvalidShowcaseException();
        }
    }
}
