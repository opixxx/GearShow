package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.vo.KitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code uniform_spec.kit_type} 가 NULL 허용으로 마이그레이션됐는지 검증한다.
 *
 * <p>ADR-016 §D3: kit_type nullable 결정의 DB 마이그레이션은 본 PR 의 schema.sql 의
 * {@code ALTER TABLE uniform_spec MODIFY COLUMN kit_type ENUM(...) NULL} 로 보강.
 * schema.sql 누락 시 NOT NULL 제약으로 'Data truncated' 또는 'cannot be null' 실패.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UniformSpecPersistenceAdapterIntegrationTest {

    @Autowired
    private UniformSpecJpaRepository uniformSpecJpaRepository;

    private UniformSpecPersistenceAdapter adapter;

    private UniformSpecPersistenceAdapter adapter() {
        if (adapter == null) {
            adapter = new UniformSpecPersistenceAdapter(uniformSpecJpaRepository, new UniformSpecMapper());
        }
        return adapter;
    }

    @Test
    @DisplayName("kitType=null 인 UniformSpec 이 schema.sql 적용 후 INSERT/SELECT 통과한다 (ADR-016 §D3)")
    void insertWithKitTypeNull_succeedsAfterSchemaSqlMigration() {
        // Given — Jordan x PSG 2025/26 4th kit 같은 케이스 (HOME/AWAY/THIRD 중 어디에도 속하지 않음)
        Long catalogItemId = 12_345L;
        UniformSpec spec = UniformSpec.create(
                catalogItemId,
                "Paris Saint-Germain",
                "파리 생제르맹",
                "2025/26",
                "Ligue 1",
                null,           // kitType — 4th kit 표기, ENUM 매핑 불가 케이스
                null
        );

        // When
        UniformSpec saved = adapter().save(spec);
        Optional<UniformSpec> found = adapter().findByCatalogItemId(catalogItemId);

        // Then
        assertThat(saved.getKitType()).isNull();
        assertThat(found).isPresent();
        assertThat(found.get().getKitType()).isNull();
        assertThat(found.get().getClubName()).isEqualTo("Paris Saint-Germain");
        assertThat(found.get().getSeason()).isEqualTo("2025/26");
    }

    @ParameterizedTest(name = "kitType={0} 정상 INSERT/SELECT round-trip")
    @EnumSource(KitType.class)
    @DisplayName("KitType 3개 enum (HOME/AWAY/THIRD) 모두 정상 round-trip — 기존 동작 회귀 가드")
    void allKitTypeEnumsAreRoundTripped(KitType kitType) {
        Long catalogItemId = 20_000L + (long) kitType.ordinal();
        UniformSpec spec = UniformSpec.create(
                catalogItemId,
                "Manchester United",
                "맨체스터 유나이티드",
                "2024/25",
                "EPL",
                kitType,
                null
        );

        UniformSpec saved = adapter().save(spec);
        Optional<UniformSpec> found = adapter().findByCatalogItemId(catalogItemId);

        assertThat(saved.getKitType()).isEqualTo(kitType);
        assertThat(found).isPresent();
        assertThat(found.get().getKitType()).isEqualTo(kitType);
    }
}
