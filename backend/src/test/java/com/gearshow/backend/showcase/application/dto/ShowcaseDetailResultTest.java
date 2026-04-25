package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShowcaseDetailResult} 의 model3d 결과 유도 규칙 단위 테스트 (ADR-010 Q4-(1)).
 */
@DisplayName("ShowcaseDetailResult.of — Model3dResult 유도")
class ShowcaseDetailResultTest {

    private Showcase newShowcase() {
        return Showcase.builder()
                .id(1L)
                .ownerId(1L)
                .category(Category.BOOTS)
                .title("t")
                .conditionGrade(ConditionGrade.A)
                .status(ShowcaseStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("완성품 존재 → COMPLETED + URL 채워짐")
    void completed_populatesUrls() {
        Showcase3dModel model = Showcase3dModel.create(1L,
                "https://cdn/m.glb", "https://cdn/p.png");

        ShowcaseDetailResult result = ShowcaseDetailResult.of(
                newShowcase(), List.of(), model, ShowcaseModelStatus.COMPLETED, null);

        assertThat(result.model3d()).isNotNull();
        assertThat(result.model3d().modelStatus()).isEqualTo(ShowcaseModelStatus.COMPLETED);
        assertThat(result.model3d().modelFileUrl()).isEqualTo("https://cdn/m.glb");
        assertThat(result.model3d().previewImageUrl()).isEqualTo("https://cdn/p.png");
    }

    @Test
    @DisplayName("workflow 진행 중 (완성품 없음) → modelStatus=GENERATING + URL 은 null")
    void generating_nullUrls() {
        ShowcaseDetailResult result = ShowcaseDetailResult.of(
                newShowcase(), List.of(), null, ShowcaseModelStatus.GENERATING, null);

        assertThat(result.model3d()).isNotNull();
        assertThat(result.model3d().modelStatus()).isEqualTo(ShowcaseModelStatus.GENERATING);
        assertThat(result.model3d().modelFileUrl()).isNull();
        assertThat(result.model3d().previewImageUrl()).isNull();
        assertThat(result.model3d().showcase3dModelId()).isNull();
    }

    @Test
    @DisplayName("workflow/완성품 모두 없음 (NONE) → model3d 자체가 null")
    void none_returnsNullModel3d() {
        ShowcaseDetailResult result = ShowcaseDetailResult.of(
                newShowcase(), List.of(), null, ShowcaseModelStatus.NONE, null);

        assertThat(result.model3d()).isNull();
    }

    @Test
    @DisplayName("FAILED — 완성품 없음, modelStatus=FAILED")
    void failed_keepsStatus() {
        ShowcaseDetailResult result = ShowcaseDetailResult.of(
                newShowcase(), List.of(), null, ShowcaseModelStatus.FAILED, null);

        assertThat(result.model3d()).isNotNull();
        assertThat(result.model3d().modelStatus()).isEqualTo(ShowcaseModelStatus.FAILED);
    }
}
