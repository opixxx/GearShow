package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchTextComposerTest {

    @Test
    @DisplayName("ADR-018 §D3: catalog 있음 — 한국어/영문 풀네임 + brand + siloNameKo + 직접 입력 모두 합쳐진다")
    void compose_withBootsCatalog_includesAllFields() {
        // Given
        Showcase showcase = newShowcase(
                100L,                        // catalogItemId
                "Nike",                      // brand
                "DJ4977-001",                // modelCode
                "내 머큐리얼 후기",            // title
                "FG 잔디에서 잘 미끄러짐"     // description
        );
        CatalogSearchSource source = new CatalogSearchSource(
                "나이키 머큐리얼 슈퍼플라이",    // fullNameKo
                "Nike Mercurial Superfly",     // fullNameEn
                "Nike",                         // brand
                "머큐리얼 슈퍼플라이",            // siloNameKo (BOOTS)
                null                            // clubNameKo
        );

        // When
        String text = SearchTextComposer.compose(showcase, source);

        // Then — catalog 한국어 alias 와 직접 입력값 모두 포함, LIKE 매칭 가능
        assertThat(text)
                .contains("나이키 머큐리얼 슈퍼플라이")
                .contains("Nike Mercurial Superfly")
                .contains("머큐리얼 슈퍼플라이")
                .contains("DJ4977-001")
                .contains("내 머큐리얼 후기")
                .contains("FG 잔디에서 잘 미끄러짐");
    }

    @Test
    @DisplayName("ADR-018 §D3: catalog 없음 — 직접 입력값만 합성된다")
    void compose_withoutCatalog_includesDirectInputOnly() {
        // Given — catalogItemId null (직접 입력 케이스)
        Showcase showcase = newShowcase(
                null, "Adidas", "GR3920", "내 직접 입력 쇼케이스", "성능 좋음");

        // When
        String text = SearchTextComposer.compose(showcase, null);

        // Then
        assertThat(text)
                .contains("Adidas")
                .contains("GR3920")
                .contains("내 직접 입력 쇼케이스")
                .contains("성능 좋음")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("ADR-018 §D3: UNIFORM 카탈로그 — clubNameKo 가 합성에 포함된다")
    void compose_withUniformCatalog_includesClubNameKo() {
        // Given
        Showcase showcase = newShowcase(
                200L, "Adidas", null, "맨유 빈티지 저지", null);
        CatalogSearchSource source = new CatalogSearchSource(
                "아디다스 맨체스터 유나이티드 1988/90",
                "Adidas Manchester United 1988/90",
                "Adidas",
                null,
                "맨체스터 유나이티드"
        );

        // When
        String text = SearchTextComposer.compose(showcase, source);

        // Then — "맨유" 검색이 가능하도록 alias 포함
        assertThat(text)
                .contains("맨체스터 유나이티드")
                .contains("Manchester United");
    }

    @Test
    @DisplayName("null/blank 토큰은 합성에서 제거된다")
    void compose_skipsNullAndBlankTokens() {
        // Given
        Showcase showcase = newShowcase(
                null, "Nike", null, "title", "  ");

        // When
        String text = SearchTextComposer.compose(showcase, null);

        // Then
        assertThat(text).isEqualTo("Nike title");
    }

    @Test
    @DisplayName("모든 토큰이 null/blank 이면 합성 결과는 null")
    void compose_allBlank_returnsNull() {
        // Given — title 은 도메인 invariant 라 blank 불가, brand 만 채워서 우회
        // 실제 시나리오: 모든 합성 토큰이 비어있는 케이스 (Showcase 생성은 정상, 합성만 빈 케이스)
        // → 도메인 검증을 통과한 객체로 Builder 직접 우회 어려움. 대신 source 없이 빈 직접 입력만 시뮬.
        Showcase showcase = Showcase.builder()
                .ownerId(1L)
                .category(Category.BOOTS)
                .brand("  ")
                .title("  ")
                .description(null)
                .build();

        // When
        String text = SearchTextComposer.compose(showcase, null);

        // Then
        assertThat(text).isNull();
    }

    @Test
    @DisplayName("ADR-018 §D3: 1000자 초과 시 truncate — VARCHAR(1000) 보호")
    void compose_overMaxLength_truncates() {
        // Given — description 에 1500자 입력
        String longDescription = "가".repeat(1500);
        Showcase showcase = newShowcase(
                null, "Nike", "MC-1", "긴 후기", longDescription);

        // When
        String text = SearchTextComposer.compose(showcase, null);

        // Then
        assertThat(text).hasSize(SearchTextComposer.MAX_LENGTH);
    }

    @Test
    @DisplayName("showcase 가 null 이면 IllegalArgumentException")
    void compose_nullShowcase_throws() {
        assertThatThrownBy(() -> SearchTextComposer.compose(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ADR-018: catalog 있어도 직접 입력값은 항상 포함 (catalog alias 부족 보강)")
    void compose_directInputAlwaysIncluded() {
        // Given — catalog 의 한국어가 짧을 때 사용자 description 으로 보강
        Showcase showcase = newShowcase(
                100L, "Nike", "DJ-1", "쇼케이스 제목", "사용자가 추가한 한국어 키워드");
        CatalogSearchSource source = new CatalogSearchSource(
                "나이키 X", "Nike X", "Nike", "X", null);

        // When
        String text = SearchTextComposer.compose(showcase, source);

        // Then
        assertThat(text)
                .contains("사용자가 추가한 한국어 키워드")
                .contains("쇼케이스 제목");
    }

    private static Showcase newShowcase(Long catalogItemId, String brand,
                                        String modelCode, String title, String description) {
        return Showcase.create(
                1L,
                catalogItemId,
                Category.BOOTS,
                brand,
                modelCode,
                title,
                description,
                "270",
                ConditionGrade.A,
                10,
                false,
                "https://example/img.jpg",
                null
        );
    }
}
