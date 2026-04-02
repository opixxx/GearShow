package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseStatusTransitionException;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowcaseTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 쇼케이스를 생성하면 ACTIVE 상태이다")
        void create_withValidInfo_returnsActiveShowcase() {
            // Given & When
            Showcase showcase = Showcase.create(1L, 1L, "테스트 쇼케이스", null, null, ConditionGrade.A, 0, false);

            // Then
            assertThat(showcase.getStatus()).isEqualTo(ShowcaseStatus.ACTIVE);
            assertThat(showcase.isForSale()).isFalse();
            assertThat(showcase.getWearCount()).isZero();
            assertThat(showcase.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("소유자 ID가 null이면 예외가 발생한다")
        void create_withNullOwnerId_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> Showcase.create(null, 1L, "테스트", null, null, ConditionGrade.A, 0, false))
                    .isInstanceOf(InvalidShowcaseException.class);
        }

        @Test
        @DisplayName("카탈로그 아이템 ID가 null이면 예외가 발생한다")
        void create_withNullCatalogItemId_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> Showcase.create(1L, null, "테스트", null, null, ConditionGrade.A, 0, false))
                    .isInstanceOf(InvalidShowcaseException.class);
        }

        @Test
        @DisplayName("제목이 빈 문자열이면 예외가 발생한다")
        void create_withBlankTitle_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> Showcase.create(1L, 1L, "  ", null, null, ConditionGrade.A, 0, false))
                    .isInstanceOf(InvalidShowcaseException.class);
        }

        @Test
        @DisplayName("상태 등급이 null이면 예외가 발생한다")
        void create_withNullConditionGrade_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> Showcase.create(1L, 1L, "테스트", null, null, null, 0, false))
                    .isInstanceOf(InvalidShowcaseException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("ACTIVE 쇼케이스를 비공개로 전환하면 HIDDEN 상태가 된다")
        void hide_fromActive_returnsHidden() {
            // Given
            Showcase showcase = Showcase.create(1L, 1L, "테스트", null, null, ConditionGrade.A, 0, false);

            // When
            Showcase hidden = showcase.hide();

            // Then
            assertThat(hidden.getStatus()).isEqualTo(ShowcaseStatus.HIDDEN);
        }

        @Test
        @DisplayName("HIDDEN 쇼케이스를 공개하면 ACTIVE 상태가 된다")
        void activate_fromHidden_returnsActive() {
            // Given
            Showcase showcase = Showcase.create(1L, 1L, "테스트", null, null, ConditionGrade.A, 0, false).hide();

            // When
            Showcase activated = showcase.activate();

            // Then
            assertThat(activated.getStatus()).isEqualTo(ShowcaseStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 쇼케이스를 삭제하면 DELETED 상태가 된다")
        void delete_fromActive_returnsDeleted() {
            // Given
            Showcase showcase = Showcase.create(1L, 1L, "테스트", null, null, ConditionGrade.A, 0, false);

            // When
            Showcase deleted = showcase.delete();

            // Then
            assertThat(deleted.getStatus()).isEqualTo(ShowcaseStatus.DELETED);
        }

        @Test
        @DisplayName("ACTIVE 쇼케이스를 판매 완료하면 SOLD 상태가 되고 판매 여부가 false가 된다")
        void markAsSold_fromActive_returnsSoldAndNotForSale() {
            // Given
            Showcase showcase = Showcase.create(1L, 1L, "테스트", null, null, ConditionGrade.A, 0, false)
                    .changeForSale(true);

            // When
            Showcase sold = showcase.markAsSold();

            // Then
            assertThat(sold.getStatus()).isEqualTo(ShowcaseStatus.SOLD);
            assertThat(sold.isForSale()).isFalse();
        }

        @Test
        @DisplayName("SOLD 쇼케이스에서 상태 전이하면 예외가 발생한다")
        void anyTransition_fromSold_throwsException() {
            // Given
            Showcase showcase = Showcase.create(1L, 1L, "테스트", null, null, ConditionGrade.A, 0, false).markAsSold();

            // When & Then
            assertThatThrownBy(showcase::hide)
                    .isInstanceOf(InvalidShowcaseStatusTransitionException.class);
            assertThatThrownBy(showcase::delete)
                    .isInstanceOf(InvalidShowcaseStatusTransitionException.class);
            assertThatThrownBy(showcase::activate)
                    .isInstanceOf(InvalidShowcaseStatusTransitionException.class);
        }

        @Test
        @DisplayName("DELETED 쇼케이스에서 상태 전이하면 예외가 발생한다")
        void anyTransition_fromDeleted_throwsException() {
            // Given
            Showcase showcase = Showcase.create(1L, 1L, "테스트", null, null, ConditionGrade.A, 0, false).delete();

            // When & Then
            assertThatThrownBy(showcase::hide)
                    .isInstanceOf(InvalidShowcaseStatusTransitionException.class);
            assertThatThrownBy(showcase::activate)
                    .isInstanceOf(InvalidShowcaseStatusTransitionException.class);
            assertThatThrownBy(showcase::markAsSold)
                    .isInstanceOf(InvalidShowcaseStatusTransitionException.class);
        }
    }
}
