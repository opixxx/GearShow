package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.showcase.adapter.out.persistence.ShowcaseJpaEntity;
import com.gearshow.backend.showcase.adapter.out.persistence.ShowcaseJpaRepository;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.UpdateShowcaseUseCase;
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
 * ADR-018: showcase.search_text 합성 + 영속 + 한국어 LIKE 회귀 통합 테스트.
 *
 * <p>기존 {@code ShowcaseServiceIntegrationTest} 와 분리한 이유: search_text 영속 검증과 한국어
 * LIKE 매칭 회귀가 PR-3 의 핵심 회귀 차단점이라, 다른 흐름의 데이터 누적/flaky 와 격리한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Transactional
class ShowcaseSearchTextIntegrationTest {

    @Autowired
    private CreateShowcaseUseCase createShowcaseUseCase;

    @Autowired
    private UpdateShowcaseUseCase updateShowcaseUseCase;

    @Autowired
    private CreateCatalogItemUseCase createCatalogItemUseCase;

    @Autowired
    private ShowcaseJpaRepository showcaseJpaRepository;

    @Test
    @DisplayName("ADR-018 §D3: catalog 있는 BOOTS 등록 시 catalog 한국어 alias + 직접 입력값이 모두 search_text 에 영속된다")
    void create_withBootsCatalog_persistsComposedSearchText() {
        // Given — catalog 등록 (PR #75 의 한국어 alias 포함)
        Long catalogItemId = createCatalogItemUseCase.create(new CreateCatalogItemCommand(
                Category.BOOTS, "Nike",
                "AT5889-174", "https://example/img.jpg",
                "나이키 머큐리얼 슈퍼플라이",
                "Nike Mercurial Superfly",
                new CreateCatalogItemCommand.BootsSpecCommand(
                        StudType.MG, "Mercurial Superfly", "머큐리얼 슈퍼플라이",
                        "2024", "MG", null),
                null
        )).catalogItemId();

        // When — Showcase 등록 (catalog 연결)
        CreateShowcaseCommand command = new CreateShowcaseCommand(
                1L, catalogItemId, Category.BOOTS, "Nike", "AT5889-174",
                "내 머큐리얼 후기", "FG 잔디에서 잘 미끄러짐",
                "270", ConditionGrade.A, 5, false, 0, false,
                null, null, null, UUID.randomUUID().toString());
        CreateShowcaseResult result = createShowcaseUseCase.create(
                command, fakeImageKeys(2), List.of());

        // Then — search_text 가 catalog 한국어 alias + 직접 입력값을 모두 포함
        ShowcaseJpaEntity entity = showcaseJpaRepository.findById(result.showcaseId()).orElseThrow();
        assertThat(entity.getSearchText())
                .contains("나이키 머큐리얼 슈퍼플라이")
                .contains("Nike Mercurial Superfly")
                .contains("머큐리얼 슈퍼플라이")
                .contains("내 머큐리얼 후기")
                .contains("FG 잔디에서 잘 미끄러짐");
    }

    @Test
    @DisplayName("ADR-018 §D3: catalog 없는 직접 입력 — 직접 입력값만으로 search_text 합성")
    void create_withoutCatalog_persistsDirectInputOnly() {
        // Given — catalogItemId=null
        CreateShowcaseCommand command = new CreateShowcaseCommand(
                1L, null, Category.BOOTS, "Adidas", "GR3920",
                "직접 입력 쇼케이스", "성능이 좋다",
                "270", ConditionGrade.A, 5, false, 0, false,
                null, null, null, UUID.randomUUID().toString());

        // When
        CreateShowcaseResult result = createShowcaseUseCase.create(
                command, fakeImageKeys(1), List.of());

        // Then — catalog 한국어 없이 직접 입력값만 합성
        ShowcaseJpaEntity entity = showcaseJpaRepository.findById(result.showcaseId()).orElseThrow();
        assertThat(entity.getSearchText())
                .contains("Adidas")
                .contains("GR3920")
                .contains("직접 입력 쇼케이스")
                .contains("성능이 좋다");
    }

    @Test
    @DisplayName("ADR-018 §D4: update 시 search_text 가 재합성되어 신규 title/description 이 반영된다")
    void update_recomposesSearchText() {
        // Given — 등록
        CreateShowcaseCommand createCmd = new CreateShowcaseCommand(
                1L, null, Category.BOOTS, "Nike", "DJ-1",
                "원래 제목", "원래 설명",
                "270", ConditionGrade.A, 5, false, 0, false,
                null, null, null, UUID.randomUUID().toString());
        CreateShowcaseResult created = createShowcaseUseCase.create(
                createCmd, fakeImageKeys(1), List.of());

        // When — title/description 변경
        UpdateShowcaseCommand updateCmd = new UpdateShowcaseCommand(
                "수정된 제목 머큐리얼", "수정된 설명", null, null, null, null, null);
        updateShowcaseUseCase.update(created.showcaseId(), 1L, updateCmd);

        // Then — search_text 재합성 (원래 title/description 사라지고 새 값 반영)
        ShowcaseJpaEntity entity = showcaseJpaRepository.findById(created.showcaseId()).orElseThrow();
        assertThat(entity.getSearchText())
                .contains("수정된 제목 머큐리얼")
                .contains("수정된 설명")
                .doesNotContain("원래 제목")
                .doesNotContain("원래 설명");
    }

    @Test
    @DisplayName("ADR-018 §D3: 한국어 부분 매칭 — '머큐리얼' 키워드로 catalog 연결 Showcase 만 search_text 에 포함")
    void searchText_supportsKoreanLikeMatching() {
        // Given — catalog 등록 + Showcase 2건 (catalog 연결 / 미연결)
        Long catalogItemId = createCatalogItemUseCase.create(new CreateCatalogItemCommand(
                Category.BOOTS, "Nike",
                "AT5889-MERC", null,
                "나이키 머큐리얼 슈퍼플라이 2024",
                "Nike Mercurial Superfly 2024",
                new CreateCatalogItemCommand.BootsSpecCommand(
                        StudType.FG, "Mercurial Superfly", "머큐리얼 슈퍼플라이",
                        "2024", "FG", null),
                null
        )).catalogItemId();

        Long matchingId = createShowcaseUseCase.create(
                new CreateShowcaseCommand(
                        1L, catalogItemId, Category.BOOTS, "Nike", "AT5889-MERC",
                        "검색 대상", "설명",
                        "270", ConditionGrade.A, 5, false, 0, false,
                        null, null, null, UUID.randomUUID().toString()),
                fakeImageKeys(1), List.of()).showcaseId();

        Long unrelatedId = createShowcaseUseCase.create(
                new CreateShowcaseCommand(
                        1L, null, Category.BOOTS, "Adidas", "GR-OTHER",
                        "다른 쇼케이스", "관련 없음",
                        "270", ConditionGrade.A, 5, false, 0, false,
                        null, null, null, UUID.randomUUID().toString()),
                fakeImageKeys(1), List.of()).showcaseId();

        // When/Then — code-reviewer M3: findAll() 의 cross-class 데이터 누적 위험 회피 위해
        // findById 로 두 행만 직접 검증.
        ShowcaseJpaEntity matching = showcaseJpaRepository.findById(matchingId).orElseThrow();
        ShowcaseJpaEntity unrelated = showcaseJpaRepository.findById(unrelatedId).orElseThrow();

        assertThat(matching.getSearchText())
                .as("catalog 연결 Showcase 의 search_text 에 catalog 한국어 alias '머큐리얼' 포함")
                .contains("머큐리얼");
        assertThat(unrelated.getSearchText())
                .as("catalog 미연결 Showcase 의 search_text 에 '머큐리얼' 미포함 (직접 입력에 없음)")
                .doesNotContain("머큐리얼");
    }

    @Test
    @DisplayName("ADR-018 §D4: content_hash dedup hit 시 기존 search_text 유지 — 재합성 X")
    void dedup_keepsExistingSearchText() {
        // Given — 동일 contentHash 로 두 번 등록 (10분 창 내 dedup hit)
        String sharedContentHash = "a".repeat(64);    // SHA-256 hex 64자 더미
        String idemKey1 = UUID.randomUUID().toString();
        String idemKey2 = UUID.randomUUID().toString();

        Long firstId = createShowcaseUseCase.create(
                new CreateShowcaseCommand(
                        1L, null, Category.BOOTS, "Nike", "DEDUP-1",
                        "원래 제목", "원래 설명",
                        "270", ConditionGrade.A, 5, false, 0, false,
                        null, null,
                        com.gearshow.backend.showcase.domain.vo.ContentHash.of(sharedContentHash),
                        idemKey1),
                fakeImageKeys(1), List.of()).showcaseId();

        ShowcaseJpaEntity firstEntity = showcaseJpaRepository.findById(firstId).orElseThrow();
        String originalSearchText = firstEntity.getSearchText();
        assertThat(originalSearchText).contains("원래 제목");

        // When — 같은 contentHash 로 다른 title/description 으로 재등록
        Long dedupedId = createShowcaseUseCase.create(
                new CreateShowcaseCommand(
                        1L, null, Category.BOOTS, "Nike", "DEDUP-1",
                        "변경된 제목 (dedup 으로 무시)", "변경된 설명",
                        "270", ConditionGrade.A, 5, false, 0, false,
                        null, null,
                        com.gearshow.backend.showcase.domain.vo.ContentHash.of(sharedContentHash),
                        idemKey2),
                fakeImageKeys(1), List.of()).showcaseId();

        // Then — dedup hit 으로 동일 ID 반환 + search_text 변경 없음 (재합성 미수행)
        assertThat(dedupedId).isEqualTo(firstId);
        ShowcaseJpaEntity dedupEntity = showcaseJpaRepository.findById(firstId).orElseThrow();
        assertThat(dedupEntity.getSearchText())
                .as("ADR-018 §D4: dedup hit 시 기존 search_text 유지 — 재합성 X")
                .isEqualTo(originalSearchText)
                .contains("원래 제목")
                .doesNotContain("변경된 제목");
    }

    private List<String> fakeImageKeys(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "showcases/images/search-text-test-" + i + ".jpg")
                .toList();
    }
}
