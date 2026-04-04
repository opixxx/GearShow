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
    }
}
