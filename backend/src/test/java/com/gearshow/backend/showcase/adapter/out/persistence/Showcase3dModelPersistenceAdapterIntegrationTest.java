package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Showcase3dModelPersistenceAdapter} 통합 테스트 (Testcontainers MySQL).
 *
 * <p>P1-G 변경 — Adapter 의 {@code save()} 가 UPSERT (INSERT 또는 기존 행 필드 업데이트) 로 동작
 * 하는지 실 DB 에서 검증한다. {@code showcase_id} UNIQUE 제약과 JPA dirty-checking 의
 * 조합이 의도대로 동작하는지를 확인.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@DisplayName("Showcase3dModelPersistenceAdapter 통합")
class Showcase3dModelPersistenceAdapterIntegrationTest {

    private static final AtomicLong SEED = new AtomicLong(0);
    private static final String MODEL_URL = "https://cdn.gearshow.com/models/x/model.glb";
    private static final String PREVIEW_URL = "https://cdn.gearshow.com/models/x/preview.png";

    @Autowired
    private Showcase3dModelPort port;

    @Autowired
    private Showcase3dModelJpaRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private long nextShowcaseId() {
        return (System.nanoTime() & 0x7FFF_FFFF_FFFFL)
                + SEED.incrementAndGet() * 10_000L;
    }

    @Nested
    @DisplayName("save UPSERT")
    class SaveUpsert {

        @Test
        @DisplayName("기존 행 없음 → INSERT (id 생성, showcaseId UNIQUE 적용)")
        void firstSave_inserts() {
            long showcaseId = nextShowcaseId();
            Showcase3dModel created = Showcase3dModel.create(showcaseId, MODEL_URL, PREVIEW_URL);

            Showcase3dModel saved = port.save(created);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getShowcaseId()).isEqualTo(showcaseId);
            assertThat(saved.getModelFileUrl()).isEqualTo(MODEL_URL);
            assertThat(saved.getPreviewImageUrl()).isEqualTo(PREVIEW_URL);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("동일 showcaseId 재저장 → UPDATE (id 보존, 산출물만 갱신)")
        void duplicateShowcaseId_updates() {
            long showcaseId = nextShowcaseId();
            Showcase3dModel first = port.save(
                    Showcase3dModel.create(showcaseId, "https://old/model.glb", "https://old/preview.png"));

            Showcase3dModel updated = port.save(
                    Showcase3dModel.create(showcaseId, MODEL_URL, PREVIEW_URL));

            assertThat(updated.getId()).isEqualTo(first.getId());
            assertThat(updated.getModelFileUrl()).isEqualTo(MODEL_URL);
            assertThat(updated.getPreviewImageUrl()).isEqualTo(PREVIEW_URL);
            assertThat(repository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("조회 메서드")
    class Lookup {

        @Test
        @DisplayName("findByShowcaseId — 존재하면 도메인 객체 반환")
        void findByShowcaseId_returnsDomain() {
            long showcaseId = nextShowcaseId();
            port.save(Showcase3dModel.create(showcaseId, MODEL_URL, PREVIEW_URL));

            Optional<Showcase3dModel> found = port.findByShowcaseId(showcaseId);

            assertThat(found).isPresent();
            assertThat(found.orElseThrow().getModelFileUrl()).isEqualTo(MODEL_URL);
        }

        @Test
        @DisplayName("findById / existsByShowcaseId / findShowcaseIdsWithModel 동작")
        void byIdAndExistsAndIn() {
            long showcaseA = nextShowcaseId();
            long showcaseB = nextShowcaseId();
            long unrelated = nextShowcaseId();
            Showcase3dModel a = port.save(Showcase3dModel.create(showcaseA, MODEL_URL, PREVIEW_URL));
            port.save(Showcase3dModel.create(showcaseB, MODEL_URL, PREVIEW_URL));

            assertThat(port.findById(a.getId())).isPresent();
            assertThat(port.existsByShowcaseId(showcaseA)).isTrue();
            assertThat(port.existsByShowcaseId(unrelated)).isFalse();

            Set<Long> ids = port.findShowcaseIdsWithModel(List.of(showcaseA, showcaseB, unrelated));
            assertThat(ids).containsExactlyInAnyOrder(showcaseA, showcaseB);
        }
    }
}
