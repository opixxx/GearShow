package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;
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
 * ADR-019 §D1: ?keyword= LIKE 매칭 통합 테스트.
 *
 * <p>핵심 회귀 차단점:</p>
 * <ul>
 *   <li>catalog 한국어 alias 매칭 (search_text 합성 효과)</li>
 *   <li>직접 입력값 매칭 (catalog 미연결)</li>
 *   <li>영문 매칭 + 대소문자 무시</li>
 *   <li>search_text NULL (backfill 미실행 행) 결과 제외</li>
 *   <li>cursor 페이징 정합성</li>
 * </ul>
 *
 * <p>다른 통합 테스트의 누적 데이터 영향을 피하기 위해 각 테스트에서 unique modelCode 또는
 * unique 키워드를 사용한다.</p>
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
    private CreateCatalogItemUseCase createCatalogItemUseCase;

    @Autowired
    private ShowcaseJpaRepository showcaseJpaRepository;

    @Test
    @DisplayName("ADR-019 §D1: catalog 한국어 alias 키워드로 검색 가능 ('머큐리얼' 키워드)")
    void search_byKoreanAliasFromCatalog() {
        // Given — 고유 토큰을 만들어 다른 테스트 데이터와 충돌 회피
        String unique = "PRB-MERC-" + UUID.randomUUID().toString().substring(0, 8);
        Long catalogItemId = createCatalogItemUseCase.create(new CreateCatalogItemCommand(
                Category.BOOTS, "Nike",
                unique, null,
                "나이키 " + unique + " 머큐리얼",
                "Nike " + unique + " Mercurial",
                new CreateCatalogItemCommand.BootsSpecCommand(
                        StudType.FG, "Mercurial Superfly", "머큐리얼 슈퍼플라이",
                        "2024", "FG", null),
                null
        )).catalogItemId();

        Long matchingId = createShowcase(1L, catalogItemId, "Nike", unique,
                "검색 대상 " + unique, "설명").showcaseId();

        // 다른 unrelated catalog 미연결 showcase
        Long unrelatedId = createShowcase(1L, null, "Adidas", "OTHER-" + unique,
                "다른 쇼케이스 " + unique, "관련 없음").showcaseId();

        // When — 한국어 alias '머큐리얼' 검색
        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list("머큐리얼", null, 20);

        // Then — catalog 연결된 matching 만 결과에 포함
        List<Long> ids = result.data().stream().map(ShowcaseListResult::showcaseId).toList();
        assertThat(ids).contains(matchingId).doesNotContain(unrelatedId);
    }

    @Test
    @DisplayName("ADR-019 §D1: 직접 입력 키워드로 검색 가능 (catalog 미연결)")
    void search_byDirectInputTitle() {
        String unique = "PRB-DIRECT-" + UUID.randomUUID().toString().substring(0, 8);
        Long matchingId = createShowcase(1L, null, "Adidas", "DIRECT-" + unique,
                unique + " 제목 직접 입력", "설명").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        assertThat(result.data())
                .extracting(ShowcaseListResult::showcaseId)
                .contains(matchingId);
    }

    @Test
    @DisplayName("ADR-019 §D1: 영문 키워드 대소문자 무시 매칭")
    void search_caseInsensitive() {
        String unique = "PRB-CASE-" + UUID.randomUUID().toString().substring(0, 8);
        Long catalogItemId = createCatalogItemUseCase.create(new CreateCatalogItemCommand(
                Category.BOOTS, "Nike",
                unique, null,
                "나이키 " + unique,
                "Nike " + unique + " ZoomFly Edition",
                new CreateCatalogItemCommand.BootsSpecCommand(
                        StudType.FG, "ZoomFly", null,
                        "2024", "FG", null),
                null
        )).catalogItemId();

        Long matchingId = createShowcase(1L, catalogItemId, "Nike", unique,
                "case test " + unique, "desc").showcaseId();

        // 대소문자 모두 매칭되어야 함
        PageInfo<ShowcaseListResult> upperResult = listShowcasesUseCase.list("ZOOMFLY", null, 20);
        PageInfo<ShowcaseListResult> lowerResult = listShowcasesUseCase.list("zoomfly", null, 20);

        assertThat(upperResult.data()).extracting(ShowcaseListResult::showcaseId).contains(matchingId);
        assertThat(lowerResult.data()).extracting(ShowcaseListResult::showcaseId).contains(matchingId);
    }

    @Test
    @DisplayName("ADR-019 §D1: search_text NULL 행은 결과에서 제외 (backfill 미실행)")
    void search_excludesNullSearchText() {
        // Given — 정상 등록 (search_text 채워짐) + JpaRepository 직접 NULL 설정한 행
        String unique = "PRB-NULL-" + UUID.randomUUID().toString().substring(0, 8);
        Long matchingId = createShowcase(1L, null, "Nike", "NULL-" + unique,
                unique + " 정상 행", "설명").showcaseId();

        // 다른 행을 search_text=NULL 로 직접 갱신 (backfill 안 한 기등록 시뮬)
        Long nullSearchTextId = createShowcase(1L, null, "Adidas", "NULL2-" + unique,
                unique + " 백필 안된 행", "설명2").showcaseId();
        ShowcaseJpaEntity entity = showcaseJpaRepository.findById(nullSearchTextId).orElseThrow();
        // 직접 NULL 로 갱신
        showcaseJpaRepository.save(toEntityWithNullSearchText(entity));

        // When
        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        // Then — search_text 채워진 matching 만 포함
        List<Long> ids = result.data().stream().map(ShowcaseListResult::showcaseId).toList();
        assertThat(ids).contains(matchingId).doesNotContain(nullSearchTextId);
    }

    @Test
    @DisplayName("ADR-019: 매칭 0건이면 빈 결과 PageInfo")
    void search_noMatchReturnsEmpty() {
        String unique = "PRB-NO-MATCH-" + UUID.randomUUID();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(unique, null, 20);

        assertThat(result.data()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("ADR-019: keyword null 이면 기존 list 동작 (전체 ACTIVE 목록)")
    void search_nullKeywordFallsBackToFullList() {
        // 신규 등록 시 result.data() 에 들어가야 함
        String unique = "PRB-FULL-" + UUID.randomUUID().toString().substring(0, 8);
        Long id = createShowcase(1L, null, "Nike", "FULL-" + unique,
                "전체 목록 테스트 " + unique, "설명").showcaseId();

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(null, null, 20);

        assertThat(result.data())
                .extracting(ShowcaseListResult::showcaseId)
                .contains(id);
    }

    private CreateShowcaseResult createShowcase(Long ownerId, Long catalogItemId,
                                                  String brand, String modelCode,
                                                  String title, String description) {
        CreateShowcaseCommand command = new CreateShowcaseCommand(
                ownerId, catalogItemId, Category.BOOTS, brand, modelCode,
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

    private ShowcaseJpaEntity toEntityWithNullSearchText(ShowcaseJpaEntity entity) {
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
                .searchText(null)   // backfill 미실행 행 시뮬
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
