package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.vo.KitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UniformSpecTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 유니폼 스펙을 생성한다")
        void create_withValidInfo_returnsSpec() {
            // Given & When
            UniformSpec spec = UniformSpec.create(1L, "Liverpool", "24-25", KitType.HOME);

            // Then
            assertThat(spec.getCatalogItemId()).isEqualTo(1L);
            assertThat(spec.getClubName()).isEqualTo("Liverpool");
            assertThat(spec.getSeason()).isEqualTo("24-25");
            assertThat(spec.getKitType()).isEqualTo(KitType.HOME);
            assertThat(spec.getCreatedAt()).isNotNull();
            assertThat(spec.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("선택 필드 없이 유니폼 스펙을 생성한다")
        void create_withoutOptionalFields_returnsSpec() {
            // Given & When
            UniformSpec spec = UniformSpec.create(2L, "Barcelona", "25-26", KitType.AWAY);

            // Then
            assertThat(spec.getLeague()).isNull();
            assertThat(spec.getExtraSpecJson()).isNull();
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
                    .clubName("맨체스터 유나이티드")
                    .season("24-25")
                    .league("EPL")
                    .kitType(KitType.THIRD)
                    .extraSpecJson("{\"material\":\"폴리에스터\"}")
                    .build();

            // Then
            assertThat(spec.getId()).isEqualTo(1L);
            assertThat(spec.getLeague()).isEqualTo("EPL");
            assertThat(spec.getKitType()).isEqualTo(KitType.THIRD);
            assertThat(spec.getExtraSpecJson()).contains("폴리에스터");
        }
    }
}
