package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.vo.StudType;
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
 * {@code boots_spec.stud_type} ENUM 정의가 {@link StudType} 의 7개 enum (FG/SG/AG/TF/IC/MG/HG)
 * 모두 INSERT/SELECT 가능한지 검증한다.
 *
 * <p>schema.sql 의 ALTER MODIFY COLUMN 이 ddl-auto 직후 적용되어 MG/HG 가 정상 동작해야 한다
 * (ADR-016 §D1 후속). schema.sql 누락 시 'Data truncated for column' 으로 실패한다.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BootsSpecPersistenceAdapterIntegrationTest {

    @Autowired
    private BootsSpecJpaRepository bootsSpecJpaRepository;

    private BootsSpecPersistenceAdapter adapter;

    private BootsSpecPersistenceAdapter adapter() {
        if (adapter == null) {
            adapter = new BootsSpecPersistenceAdapter(bootsSpecJpaRepository, new BootsSpecMapper());
        }
        return adapter;
    }

    @ParameterizedTest(name = "studType={0} 가 schema.sql 적용 후 INSERT/SELECT 통과한다")
    @EnumSource(StudType.class)
    @DisplayName("StudType 7개 enum 모두 boots_spec 에 영속/round-trip 된다 (ADR-016 §D1)")
    void allStudTypeEnumsArePersistedAndRetrieved(StudType studType) {
        // Given — 임의 catalogItemId (FK 검증 없음 — 본 테스트는 ENUM 영속성만 대상)
        Long catalogItemId = (long) (studType.ordinal() + 1) * 10_000L;
        BootsSpec spec = BootsSpec.create(
                catalogItemId,
                studType,
                "Mercurial Vapor",
                "머큐리얼 베이퍼",
                "2024",
                "혼합 잔디",
                null
        );

        // When
        BootsSpec saved = adapter().save(spec);
        Optional<BootsSpec> found = adapter().findByCatalogItemId(catalogItemId);

        // Then
        assertThat(saved.getStudType()).isEqualTo(studType);
        assertThat(found).isPresent();
        assertThat(found.get().getStudType()).isEqualTo(studType);
    }

    @Test
    @DisplayName("MG enum 회귀 가드: schema.sql 미적용 시 'Data truncated' 로 실패하는 케이스 명시")
    void studTypeMG_isExplicitlyCovered() {
        // 본 테스트는 위 ParameterizedTest 가 MG 도 cover 하지만, 회귀 시 어떤 enum 이 깨졌는지
        // 빠르게 식별하기 위한 단독 가드.
        BootsSpec spec = BootsSpec.create(
                999_999L,
                StudType.MG,
                "Mercurial Vapor",
                "머큐리얼 베이퍼",
                "2024",
                "혼합 잔디",
                null
        );

        BootsSpec saved = adapter().save(spec);

        assertThat(saved.getStudType()).isEqualTo(StudType.MG);
    }
}
