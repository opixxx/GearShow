package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.StudType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootsSpecTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 축구화 스펙을 생성한다")
        void create_withValidInfo_returnsSpec() {
            // Given & When
            BootsSpec spec = BootsSpec.create(
                    1L, StudType.FG,
                    "Mercurial Superfly", "머큐리얼 슈퍼플라이",
                    "2024", "FG", null);

            // Then
            assertThat(spec.getCatalogItemId()).isEqualTo(1L);
            assertThat(spec.getStudType()).isEqualTo(StudType.FG);
            assertThat(spec.getSiloName()).isEqualTo("Mercurial Superfly");
            assertThat(spec.getSiloNameKo()).isEqualTo("머큐리얼 슈퍼플라이");
            assertThat(spec.getReleaseYear()).isEqualTo("2024");
            assertThat(spec.getSurfaceType()).isEqualTo("FG");
            assertThat(spec.getCreatedAt()).isNotNull();
            assertThat(spec.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("siloNameKo 는 nullable — 한국어 alias 없이도 생성된다")
        void create_withNullSiloNameKo_isAllowed() {
            // Given & When
            BootsSpec spec = BootsSpec.create(
                    1L, StudType.FG,
                    "Predator", null,
                    "2024", "FG", null);

            // Then
            assertThat(spec.getSiloName()).isEqualTo("Predator");
            assertThat(spec.getSiloNameKo()).isNull();
        }

        @ParameterizedTest
        @EnumSource(StudType.class)
        @DisplayName("ADR-016: 7개 StudType (FG/SG/AG/TF/IC/MG/HG) 모두 허용")
        void create_withEachStudType_isAllowed(StudType studType) {
            // Given & When
            BootsSpec spec = BootsSpec.create(
                    1L, studType,
                    "Some Silo", null,
                    "2024", studType.name(), null);

            // Then
            assertThat(spec.getStudType()).isEqualTo(studType);
        }

        @Test
        @DisplayName("ADR-016: MG (Multi Ground — 혼합 잔디) 케이스")
        void create_withMG_returnsMultiGroundSpec() {
            // Given & When
            BootsSpec spec = BootsSpec.create(
                    1L, StudType.MG,
                    "Mercurial Vapor", "머큐리얼 베이퍼",
                    "2024", "MG", null);

            // Then
            assertThat(spec.getStudType()).isEqualTo(StudType.MG);
            assertThat(spec.getSurfaceType()).isEqualTo("MG");
        }

        @Test
        @DisplayName("ADR-016: HG (Hard Ground — 단단한 천연잔디) 케이스")
        void create_withHG_returnsHardGroundSpec() {
            // Given & When
            BootsSpec spec = BootsSpec.create(
                    2L, StudType.HG,
                    "Predator", "프레데터",
                    "2023", "HG", null);

            // Then
            assertThat(spec.getStudType()).isEqualTo(StudType.HG);
            assertThat(spec.getSurfaceType()).isEqualTo("HG");
        }

        @Test
        @DisplayName("카탈로그 아이템 ID가 null이면 예외가 발생한다")
        void create_withNullCatalogItemId_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> BootsSpec.create(
                    null, StudType.FG,
                    "Mercurial", null,
                    "2024", "FG", null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }

        @Test
        @DisplayName("StudType이 null이면 예외가 발생한다")
        void create_withNullStudType_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> BootsSpec.create(
                    1L, null,
                    "Mercurial", null,
                    "2024", "FG", null))
                    .isInstanceOf(InvalidCatalogItemException.class);
        }
    }

    @Nested
    @DisplayName("builder")
    class BuilderTest {

        @Test
        @DisplayName("빌더로 모든 필드를 포함한 축구화 스펙을 생성한다")
        void builder_withAllFields_returnsFullSpec() {
            // Given & When
            BootsSpec spec = BootsSpec.builder()
                    .id(1L)
                    .catalogItemId(10L)
                    .studType(StudType.MG)
                    .siloName("Mercurial Superfly")
                    .siloNameKo("머큐리얼 슈퍼플라이")
                    .releaseYear("2024")
                    .surfaceType("MG")
                    .extraSpecJson("{\"weight\":\"190g\"}")
                    .build();

            // Then
            assertThat(spec.getId()).isEqualTo(1L);
            assertThat(spec.getStudType()).isEqualTo(StudType.MG);
            assertThat(spec.getSiloNameKo()).isEqualTo("머큐리얼 슈퍼플라이");
            assertThat(spec.getExtraSpecJson()).contains("190g");
        }
    }
}
