package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.adapter.out.persistence.ShowcaseJpaEntity;
import com.gearshow.backend.showcase.adapter.out.persistence.ShowcaseJpaRepository;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.ListShowcasesUseCase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-024 §D2: ?keyword= 검색이 title 또는 description 직접 LIKE OR 로 동작함을 검증한다.
 *
 * <p>회귀 차단점:</p>
 * <ul>
 *   <li>title 부분 매칭</li>
 *   <li>description 부분 매칭</li>
 *   <li>title/description 모두 미매칭 → 0건</li>
 *   <li>대소문자 무시 (collation utf8mb4_0900_ai_ci)</li>
 *   <li>LIKE wildcard '%' / '_' escape (amplification 차단)</li>
 *   <li>HIDDEN/SOLD/DELETED 결과 제외</li>
 *   <li>keyset 페이징 정합성</li>
 *   <li>blank keyword → 전체 ACTIVE fallback</li>
 *   <li>brand=null 직접 등록 행도 title 매칭으로 검색됨 (ADR-024 §D3 회귀 가드)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Transactional
class ShowcaseSearchKeywordIntegrationTest {

    @Autowired
    private CreateShowcaseUseCase createShowcaseUseCase;

    @Autowired
    private ListShowcasesUseCase listShowcasesUseCase;

    @Autowired
    private ShowcaseJpaRepository showcaseJpaRepository;

    @Test
    @DisplayName("ADR-024 §D2: title 부분 매칭으로 검색")
    void search_byTitleMatch() {
        String unique = "PRB-TITLE-" + UUID.randomUUID().toString().substring(0, 8);
        Long matchingId = createShowcase(1L, "Nike",
                unique + " 머큐리얼 후기", "본문 설명").showcaseId();
        Long unrelatedId = createShowcase(1L, "Adidas",
                "다른 쇼케이스", "관련 없음").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        List<Long> ids = result.data().stream().map(ShowcaseListResult::showcaseId).toList();
        assertThat(ids).contains(matchingId).doesNotContain(unrelatedId);
    }

    @Test
    @DisplayName("ADR-024 §D2: description 부분 매칭으로 검색")
    void search_byDescriptionMatch() {
        String unique = "PRB-DESC-" + UUID.randomUUID().toString().substring(0, 8);
        Long matchingId = createShowcase(1L, "Nike",
                "제목은 평범", "본문에 " + unique + " 가 들어있음").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        assertThat(result.data())
                .extracting(ShowcaseListResult::showcaseId)
                .contains(matchingId);
    }

    @Test
    @DisplayName("ADR-024 §D2: title 또는 description 매칭 (OR 동작)")
    void search_byTitleOrDescription() {
        String unique = "PRB-OR-" + UUID.randomUUID().toString().substring(0, 8);
        Long titleHit = createShowcase(1L, "Nike",
                unique + " 제목 매칭", "본문 무관").showcaseId();
        Long descHit = createShowcase(1L, "Nike",
                "제목 무관", "본문 매칭 " + unique).showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        List<Long> ids = result.data().stream().map(ShowcaseListResult::showcaseId).toList();
        assertThat(ids).contains(titleHit, descHit);
    }

    @Test
    @DisplayName("ADR-024 §D2: 영문 키워드 대소문자 무시 매칭")
    void search_caseInsensitive() {
        String unique = "PRBCASE" + UUID.randomUUID().toString().substring(0, 8);
        Long matchingId = createShowcase(1L, "Nike",
                "case test " + unique + " ZoomFly", "desc").showcaseId();

        PageInfo<ShowcaseListResult> upperResult = listShowcasesUseCase.list("ZOOMFLY", null, 20);
        PageInfo<ShowcaseListResult> lowerResult = listShowcasesUseCase.list("zoomfly", null, 20);

        assertThat(upperResult.data()).extracting(ShowcaseListResult::showcaseId).contains(matchingId);
        assertThat(lowerResult.data()).extracting(ShowcaseListResult::showcaseId).contains(matchingId);
    }

    @Test
    @DisplayName("ADR-024 §D2: 매칭 0건이면 빈 결과 PageInfo")
    void search_noMatchReturnsEmpty() {
        String unique = "PRB-NO-MATCH-" + UUID.randomUUID();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        assertThat(result.data()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("ADR-024 §D2: keyword null 이면 기존 list 동작 (전체 ACTIVE 목록)")
    void search_nullKeywordFallsBackToFullList() {
        String unique = "PRB-FULL-" + UUID.randomUUID().toString().substring(0, 8);
        Long id = createShowcase(1L, "Nike",
                "전체 목록 테스트 " + unique, "설명").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(null, null, 20);

        assertThat(result.data())
                .extracting(ShowcaseListResult::showcaseId)
                .contains(id);
    }

    @Test
    @DisplayName("ADR-024 §D2: keyword 검색 결과가 size 초과 시 hasNext=true + pageToken + 2페이지 정합성")
    void search_paging_hasNextWhenOverSize() {
        String unique = "PRB-PAGE-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            createShowcase(1L, "Nike",
                    unique + " 페이징 " + i, "설명");
        }

        PageInfo<ShowcaseListResult> page1 = listShowcasesUseCase.list(unique, null, 2);

        assertThat(page1.data()).hasSize(2);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.pageToken()).isNotNull();

        PageInfo<ShowcaseListResult> page2 = listShowcasesUseCase.list(unique, page1.pageToken(), 2);

        assertThat(page2.data()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();
        long combined = java.util.stream.Stream.concat(
                page1.data().stream(), page2.data().stream()
        ).map(ShowcaseListResult::showcaseId).distinct().count();
        assertThat(combined).isEqualTo(3);
    }

    @Test
    @DisplayName("ADR-024 §D2: LIKE wildcard '%' / '_' escape — amplification 차단")
    void search_likeWildcardsAreEscaped_noAmplification() {
        String unique = "PRB-WC-" + UUID.randomUUID().toString().substring(0, 8);
        Long uniqueId = createShowcase(1L, "Nike",
                unique + " wildcard 확인용", "설명").showcaseId();

        PageInfo<ShowcaseListResult> percentResult = listShowcasesUseCase.list("%", null, 20);

        assertThat(percentResult.data())
                .as("LIKE wildcard '%' 가 escape 되지 않으면 unique 행이 매칭됨 (amplification)")
                .filteredOn(r -> r.showcaseId().equals(uniqueId))
                .isEmpty();
    }

    @Test
    @DisplayName("ADR-024 §D2: status 가드 — HIDDEN 검색 결과 제외")
    void search_excludesNonActiveStatus() {
        String unique = "PRB-STATUS-" + UUID.randomUUID().toString().substring(0, 8);
        Long activeId = createShowcase(1L, "Nike",
                unique + " ACTIVE 행", "설명").showcaseId();
        Long hiddenId = createShowcase(1L, "Nike",
                unique + " HIDDEN 행", "설명").showcaseId();

        ShowcaseJpaEntity hidden = showcaseJpaRepository.findById(hiddenId).orElseThrow();
        showcaseJpaRepository.save(toEntityWithStatus(hidden,
                com.gearshow.backend.showcase.domain.vo.ShowcaseStatus.HIDDEN));

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        List<Long> ids = result.data().stream().map(ShowcaseListResult::showcaseId).toList();
        assertThat(ids).contains(activeId).doesNotContain(hiddenId);
    }

    @Test
    @DisplayName("ADR-024 §D2: keyword 100자 통과 (Controller @Size 경계는 Service 외부 책임)")
    void search_keywordLength100_passes() {
        String keyword100 = "가".repeat(100);

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(keyword100, null, 20);

        assertThat(result.data()).isNotNull();
    }

    @Test
    @DisplayName("ADR-024 §D2: 공백만 keyword 는 fallback (전체 ACTIVE 목록)")
    void search_blankKeywordFallsBackToFullList() {
        String unique = "PRB-BLANK-" + UUID.randomUUID().toString().substring(0, 8);
        Long id = createShowcase(1L, "Nike",
                unique + " blank 테스트", "설명").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list("   ", null, 20);

        assertThat(result.data())
                .extracting(ShowcaseListResult::showcaseId)
                .contains(id);
    }

    @Test
    @DisplayName("ADR-024 §D3: brand=null 직접 등록 행도 title 매칭으로 검색됨 (회귀 가드)")
    void search_brandNullEntryStillMatchesByTitle() {
        String unique = "PRB-BRAND-NULL-" + UUID.randomUUID().toString().substring(0, 8);
        Long matchingId = createShowcase(1L, null,
                unique + " brand null 행", "설명").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        assertThat(result.data())
                .extracting(ShowcaseListResult::showcaseId)
                .contains(matchingId);
    }

    private CreateShowcaseResult createShowcase(Long ownerId, String brand,
                                                String title, String description) {
        CreateShowcaseCommand command = new CreateShowcaseCommand(
                ownerId, null, Category.BOOTS, brand, null,
                title, description,
                "270", ConditionGrade.A, 5, false, 0, false,
                null, null, null, UUID.randomUUID().toString());
        return createShowcaseUseCase.create(command, fakeImageKeys(1), List.of());
    }

    private List<String> fakeImageKeys(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "showcases/images/keyword-test-" + i + "-" + UUID.randomUUID() + ".jpg")
                .toList();
    }

    private ShowcaseJpaEntity toEntityWithStatus(
            ShowcaseJpaEntity entity,
            com.gearshow.backend.showcase.domain.vo.ShowcaseStatus newStatus) {
        return ShowcaseJpaEntity.builder()
                .id(entity.getId())
                .ownerId(entity.getOwnerId())
                .catalogItemId(entity.getCatalogItemId())
                .category(entity.getCategory())
                .brand(entity.getBrand())
                .modelCode(entity.getModelCode())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .userSize(entity.getUserSize())
                .conditionGrade(entity.getConditionGrade())
                .wearCount(entity.getWearCount())
                .forSale(entity.isForSale())
                .primaryImageUrl(entity.getPrimaryImageUrl())
                .contentHash(entity.getContentHash())
                .has3dModel(entity.isHas3dModel())
                .status(newStatus)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
