package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.vo.SpecType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowcaseSpecTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 스펙을 생성한다")
        void create_withValidInfo_success() {
            // Given & When
            ShowcaseSpec spec = ShowcaseSpec.create(1L, SpecType.BOOTS,
                    "{\"studType\":\"FG\",\"siloName\":\"Mercurial\"}");

            // Then
            assertThat(spec.getShowcaseId()).isEqualTo(1L);
            assertThat(spec.getSpecType()).isEqualTo(SpecType.BOOTS);
            assertThat(spec.getSpecData()).contains("FG");
            assertThat(spec.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("showcaseId가 null이면 예외가 발생한다")
        void create_withNullShowcaseId_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> ShowcaseSpec.create(null, SpecType.BOOTS, "{\"studType\":\"FG\"}"))
                    .isInstanceOf(InvalidShowcaseException.class);
        }

        @Test
        @DisplayName("specType이 null이면 예외가 발생한다")
        void create_withNullSpecType_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> ShowcaseSpec.create(1L, null, "{\"studType\":\"FG\"}"))
                    .isInstanceOf(InvalidShowcaseException.class);
        }

        @Test
        @DisplayName("specData가 빈 문자열이면 예외가 발생한다")
        void create_withBlankSpecData_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> ShowcaseSpec.create(1L, SpecType.BOOTS, "  "))
                    .isInstanceOf(InvalidShowcaseException.class);
        }

        @Test
        @DisplayName("specData가 null이면 예외가 발생한다")
        void create_withNullSpecData_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> ShowcaseSpec.create(1L, SpecType.BOOTS, null))
                    .isInstanceOf(InvalidShowcaseException.class);
        }
    }
}
