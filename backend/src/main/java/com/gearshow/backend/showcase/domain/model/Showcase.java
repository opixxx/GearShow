package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseStatusTransitionException;
import com.gearshow.backend.showcase.domain.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ContentHash;
import com.gearshow.backend.showcase.domain.vo.ShowcaseUpdate;
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
    /** 이미지 조합의 SHA-256 해시. 10분 창 내 중복 등록 감지용. null 허용 (기존 데이터 보호). */
    private final ContentHash contentHash;
    private final boolean has3dModel;
    /**
     * 검색 보강용 합성 텍스트 (ADR-018).
     *
     * <p>catalog 의 fullNameKo/En + brand + siloNameKo/clubNameKo 와 직접 입력값
     * (brand, modelCode, title, description) 을 공백 join 한 결과. application 의
     * {@code SearchTextComposer} 가 합성하여 도메인에 주입한다 (도메인은 합성 책임 X).</p>
     *
     * <p>nullable — 합성 누락 시 등록 자체는 차단하지 않는다. 후속 PR-4 의 LIKE 검색이
     * 누락된 행을 검색 결과에서 제외할 뿐.</p>
     */
    private final String searchText;
    private final ShowcaseStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private Showcase(Long id, Long ownerId, Long catalogItemId,
                     Category category, String brand, String modelCode,
                     String title, String description, String userSize,
                     ConditionGrade conditionGrade, int wearCount, boolean forSale,
                     String primaryImageUrl, ContentHash contentHash, boolean has3dModel,
                     String searchText,
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
        this.searchText = searchText;
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
                                  String primaryImageUrl,
                                  ContentHash contentHash) {
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
                .contentHash(contentHash)
                .has3dModel(false)
                // ADR-018 §D4: create() 직후 searchText 는 null. application 의
                // SearchTextSynchronizer 가 합성 후 changeSearchText 로 주입한다.
                // 명시적 null 은 Builder default 변경에도 invariant 를 보호하기 위함.
                .searchText(null)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 검색 텍스트를 새로 주입한다 (ADR-018 §D2).
     *
     * <p>application 의 {@code SearchTextSynchronizer} 가 catalog 의 한국어 alias 와
     * 직접 입력값을 합성하여 호출한다. 도메인은 단순 보존만 담당.</p>
     *
     * <p><b>ADR-018 §D4 invariant</b>: 본 메서드 외에는 search_text 를 변경하지 않는다.
     * 다른 changeXxx (changePrimaryImageUrl, changeHas3dModel, changeForSale) 와 상태 전이
     * 메서드 (hide/activate/markAsSold/delete) 는 모두 {@code toBuilder()} 로 기존 search_text 를
     * 보존한다. 이를 우회하면 search_text stale 위험 발생.</p>
     *
     * @param searchText 새 검색 텍스트 (nullable — 합성 누락 시 등록 자체는 차단하지 않음)
     * @return 변경된 쇼케이스
     */
    public Showcase changeSearchText(String searchText) {
        return toBuilder()
                .searchText(searchText)
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
     * {@link ShowcaseUpdate}의 null이 아닌 필드만 변경된다 (Partial Update).
     *
     * @param update 변경할 필드를 묶은 값 객체
     * @return 수정된 쇼케이스
     */
    public Showcase update(ShowcaseUpdate update) {
        if (update.title() != null && update.title().isBlank()) {
            throw new InvalidShowcaseException();
        }

        return toBuilder()
                .title(update.title() != null ? update.title() : this.title)
                .description(update.description() != null ? update.description() : this.description)
                .modelCode(update.modelCode() != null ? update.modelCode() : this.modelCode)
                .userSize(update.userSize() != null ? update.userSize() : this.userSize)
                .conditionGrade(update.conditionGrade() != null ? update.conditionGrade() : this.conditionGrade)
                .wearCount(update.wearCount() != null ? update.wearCount() : this.wearCount)
                .forSale(update.forSale() != null ? update.forSale() : this.forSale)
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
                .contentHash(this.contentHash)
                .has3dModel(this.has3dModel)
                .searchText(this.searchText)
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
