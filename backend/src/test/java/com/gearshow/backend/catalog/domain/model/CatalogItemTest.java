package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogItemTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 카탈로그 아이템을 생성하면 ACTIVE 상태이다")
        void create_withValidInfo_returnsActiveItem() {
            // Given & When
            CatalogItem item = CatalogItem.create(Category.BOOTS, "Nike");

            // Then
            assertThat(item.getCategory()).isEqualTo(Category.BOOTS);
            assertThat(item.getBrand()).isEqualTo("Nike");
            assertThat(item.getStatus()).isEqualTo(CatalogStatus.ACTIVE);
            assertThat(item.isActive()).isTrue();
            assertThat(item.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("카테고리가 null이면 예외가 발생한다")
        void create_withNullCategory_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> CatalogItem.create(null, "Nike"))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }

        @Test
        @DisplayName("브랜드가 null이면 예외가 발생한다")
        void create_withNullBrand_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> CatalogItem.create(Category.BOOTS, null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }

        @Test
        @DisplayName("브랜드가 빈 문자열이면 예외가 발생한다")
        void create_withBlankBrand_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> CatalogItem.create(Category.UNIFORM, "  "))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }

        @Test
        @DisplayName("ADR-016: 한국어/영문 풀네임 포함하여 생성한다")
        void create_withFullNames_returnsItemWithBothNames() {
            // Given & When
            CatalogItem item = CatalogItem.create(
                    Category.BOOTS, "Nike", "AT5889-174", "https://example/img.jpg",
                    "나이키 프리미어 3 FG 화이트 메탈릭 골드",
                    "Nike Premier 3 FG White Metallic Gold");

            // Then
            assertThat(item.getFullNameKo()).isEqualTo("나이키 프리미어 3 FG 화이트 메탈릭 골드");
            assertThat(item.getFullNameEn()).isEqualTo("Nike Premier 3 FG White Metallic Gold");
            assertThat(item.getModelCode()).isEqualTo("AT5889-174");
            assertThat(item.getOfficialImageUrl()).isEqualTo("https://example/img.jpg");
            assertThat(item.isActive()).isTrue();
        }

        @Test
        @DisplayName("한국어/영문 풀네임은 nullable — 사용자 직접 입력 케이스")
        void create_withNullFullNames_isAllowed() {
            // Given & When (사용자 직접 입력 시 풀네임 미제공)
            CatalogItem item = CatalogItem.create(
                    Category.UNIFORM, "Adidas", "GR3920", null, null, null);

            // Then
            assertThat(item.getFullNameKo()).isNull();
            assertThat(item.getFullNameEn()).isNull();
            assertThat(item.getBrand()).isEqualTo("Adidas");
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateMethod {

        @Test
        @DisplayName("ADR-016: 한국어 풀네임 정정이 가능하다 (다른 필드 보존)")
        void update_correctsKoreanFullName_preservesOthers() {
            // Given
            CatalogItem item = CatalogItem.create(
                    Category.BOOTS, "Nike", "AT5889-174", null,
                    "잘못된 한국어", "Nike Premier 3");

            // When
            CatalogItem updated = item.update(null, null, null,
                    "나이키 프리미어 3", null);

            // Then
            assertThat(updated.getFullNameKo()).isEqualTo("나이키 프리미어 3");
            assertThat(updated.getFullNameEn()).isEqualTo("Nike Premier 3");
            assertThat(updated.getBrand()).isEqualTo("Nike");
            assertThat(updated.getModelCode()).isEqualTo("AT5889-174");
        }

        @Test
        @DisplayName("ADR-016: brand 만 수정해도 한국어/영문 풀네임이 보존된다")
        void update_brandOnly_preservesFullNames() {
            // Given
            CatalogItem item = CatalogItem.create(
                    Category.BOOTS, "Nike", null, null,
                    "나이키 프리미어 3", "Nike Premier 3");

            // When
            CatalogItem updated = item.update("Adidas", null, null, null, null);

            // Then
            assertThat(updated.getBrand()).isEqualTo("Adidas");
            assertThat(updated.getFullNameKo()).isEqualTo("나이키 프리미어 3");
            assertThat(updated.getFullNameEn()).isEqualTo("Nike Premier 3");
        }

        @Test
        @DisplayName("brand 가 빈 문자열이면 예외가 발생한다")
        void update_blankBrand_throwsException() {
            // Given
            CatalogItem item = CatalogItem.create(Category.BOOTS, "Nike");

            // When & Then
            assertThatThrownBy(() -> item.update("  ", null, null, null, null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("카탈로그 아이템을 비활성화하면 INACTIVE 상태가 된다")
        void deactivate_returnsInactiveItem() {
            // Given
            CatalogItem item = CatalogItem.create(Category.BOOTS, "Adidas");

            // When
            CatalogItem deactivated = item.deactivate();

            // Then
            assertThat(deactivated.getStatus()).isEqualTo(CatalogStatus.INACTIVE);
            assertThat(deactivated.isActive()).isFalse();
            assertThat(deactivated.getBrand()).isEqualTo("Adidas");
        }

        @Test
        @DisplayName("ADR-016: 비활성화해도 한국어/영문 풀네임이 보존된다")
        void deactivate_preservesFullNames() {
            // Given
            CatalogItem item = CatalogItem.create(
                    Category.BOOTS, "Nike", "AT5889-174", null,
                    "나이키 프리미어 3", "Nike Premier 3");

            // When
            CatalogItem deactivated = item.deactivate();

            // Then
            assertThat(deactivated.isActive()).isFalse();
            assertThat(deactivated.getFullNameKo()).isEqualTo("나이키 프리미어 3");
            assertThat(deactivated.getFullNameEn()).isEqualTo("Nike Premier 3");
        }
    }
}
