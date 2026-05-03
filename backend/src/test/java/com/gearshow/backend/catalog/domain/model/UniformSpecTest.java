package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.KitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniformSpecTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 유니폼 스펙을 생성한다")
        void create_withValidInfo_returnsSpec() {
            // Given & When
            UniformSpec spec = UniformSpec.create(
                    1L, "Liverpool", "리버풀", "24-25", "EPL", KitType.HOME, null);

            // Then
            assertThat(spec.getCatalogItemId()).isEqualTo(1L);
            assertThat(spec.getClubName()).isEqualTo("Liverpool");
            assertThat(spec.getClubNameKo()).isEqualTo("리버풀");
            assertThat(spec.getSeason()).isEqualTo("24-25");
            assertThat(spec.getKitType()).isEqualTo(KitType.HOME);
            assertThat(spec.getCreatedAt()).isNotNull();
            assertThat(spec.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("필수 필드만으로 유니폼 스펙을 생성한다 (한국어 alias / kitType 미사용)")
        void create_withRequiredOnly_returnsSpec() {
            // Given & When
            UniformSpec spec = UniformSpec.create(2L, "Barcelona", "25-26");

            // Then
            assertThat(spec.getLeague()).isNull();
            assertThat(spec.getKitType()).isNull();
            assertThat(spec.getClubNameKo()).isNull();
            assertThat(spec.getExtraSpecJson()).isNull();
        }

        @Test
        @DisplayName("ADR-016: 빈티지 유니폼 케이스 — kitType null 허용")
        void create_withNullKitType_isAllowed() {
            // Given & When (1988/90 빈티지 저지 — Home/Away 미명시)
            UniformSpec spec = UniformSpec.create(
                    3L, "Manchester United", "맨체스터 유나이티드",
                    "1988/90", "EPL", null, null);

            // Then — 예외 없이 생성됨
            assertThat(spec.getKitType()).isNull();
            assertThat(spec.getSeason()).isEqualTo("1988/90");
        }

        @Test
        @DisplayName("국가대표 유니폼 — league null 허용")
        void create_withNullLeague_isAllowedForNationalTeam() {
            // Given & When (Korea 국가대표 — league 없음)
            UniformSpec spec = UniformSpec.create(
                    4L, "Korea", "대한민국",
                    "24-25", null, KitType.HOME, null);

            // Then
            assertThat(spec.getLeague()).isNull();
            assertThat(spec.getClubName()).isEqualTo("Korea");
            assertThat(spec.getClubNameKo()).isEqualTo("대한민국");
        }

        @Test
        @DisplayName("카탈로그 아이템 ID가 null이면 예외가 발생한다")
        void create_withNullCatalogItemId_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() ->
                    UniformSpec.create(null, "Liverpool", "리버풀", "24-25", "EPL", KitType.HOME, null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }

        @Test
        @DisplayName("클럽명이 빈 문자열이면 예외가 발생한다")
        void create_withBlankClubName_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() ->
                    UniformSpec.create(1L, "  ", null, "24-25", null, KitType.HOME, null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }

        @Test
        @DisplayName("시즌이 빈 문자열이면 예외가 발생한다")
        void create_withBlankSeason_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() ->
                    UniformSpec.create(1L, "Liverpool", null, "  ", null, KitType.HOME, null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTest {

        @Test
        @DisplayName("빌더로 모든 필드를 포함한 유니폼 스펙을 생성한다")
        void builder_withAllFields_returnsFullSpec() {
            // Given & When
            UniformSpec spec = UniformSpec.builder()
                    .id(1L)
                    .catalogItemId(10L)
                    .clubName("Manchester United")
                    .clubNameKo("맨체스터 유나이티드")
                    .season("24-25")
                    .league("EPL")
                    .kitType(KitType.THIRD)
                    .extraSpecJson("{\"material\":\"폴리에스터\"}")
                    .build();

            // Then
            assertThat(spec.getId()).isEqualTo(1L);
            assertThat(spec.getClubNameKo()).isEqualTo("맨체스터 유나이티드");
            assertThat(spec.getLeague()).isEqualTo("EPL");
            assertThat(spec.getKitType()).isEqualTo(KitType.THIRD);
            assertThat(spec.getExtraSpecJson()).contains("폴리에스터");
        }
    }
}
