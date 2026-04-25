package com.gearshow.backend.showcase.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Showcase3dModel} 단위 테스트 (ADR-010 완성품 전용 아키텍처).
 *
 * <p>P1-G 이후 {@code Showcase3dModel} 은 상태 전이가 없는 완성품 스냅샷이다.
 * 변경은 Persistence Adapter 의 UPSERT 가 책임지므로 도메인은 {@code create(...)} 만 노출한다.</p>
 */
@DisplayName("Showcase3dModel")
class Showcase3dModelTest {

    private static final Long SHOWCASE_ID = 1L;
    private static final String MODEL_URL = "https://cdn.gearshow.com/models/1/model.glb";
    private static final String PREVIEW_URL = "https://cdn.gearshow.com/models/1/preview.png";

    @Test
    @DisplayName("create: 필수 필드가 모두 채워진 완성품 스냅샷이 생성된다")
    void create_populatesCompletedArtifact() {
        Showcase3dModel model = Showcase3dModel.create(SHOWCASE_ID, MODEL_URL, PREVIEW_URL);

        assertThat(model.getShowcaseId()).isEqualTo(SHOWCASE_ID);
        assertThat(model.getModelFileUrl()).isEqualTo(MODEL_URL);
        assertThat(model.getPreviewImageUrl()).isEqualTo(PREVIEW_URL);
        assertThat(model.getGeneratedAt()).isNotNull();
        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getUpdatedAt()).isNotNull();
        assertThat(model.getId()).isNull();
    }
}
