package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShowcaseModelStatusResolver} 단위 테스트 (ADR-010 Q4-(1) 유도 규칙).
 */
@DisplayName("ShowcaseModelStatusResolver")
class ShowcaseModelStatusResolverTest {

    @Nested
    @DisplayName("완성품 존재 시")
    class WithCompletedArtifact {

        @ParameterizedTest(name = "workflow.currentStep={0} 이어도 COMPLETED")
        @CsvSource({"REQUESTED", "PREPARING", "GENERATING", "COMPLETED", "FAILED"})
        @DisplayName("workflow 단계와 무관하게 COMPLETED 가 우선")
        void artifactWins(WorkflowStep step) {
            assertThat(ShowcaseModelStatusResolver.resolve(true, step))
                    .isEqualTo(ShowcaseModelStatus.COMPLETED);
        }

        @Test
        @DisplayName("workflow 가 없어도 완성품 존재 시 COMPLETED")
        void noWorkflowButArtifact() {
            assertThat(ShowcaseModelStatusResolver.resolve(true, null))
                    .isEqualTo(ShowcaseModelStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("완성품 없음")
    class NoArtifact {

        @ParameterizedTest(name = "currentStep={0} → expected={1}")
        @CsvSource({
                "REQUESTED, GENERATING",
                "PREPARING, GENERATING",
                "GENERATING, GENERATING",
                "COMPLETED, COMPLETED",
                "FAILED, FAILED"
        })
        void mappedFromWorkflowStep(WorkflowStep step, ShowcaseModelStatus expected) {
            assertThat(ShowcaseModelStatusResolver.resolve(false, step)).isEqualTo(expected);
        }

        @Test
        @DisplayName("workflow 도 없으면 NONE")
        void neither_returnsNone() {
            assertThat(ShowcaseModelStatusResolver.resolve(false, null))
                    .isEqualTo(ShowcaseModelStatus.NONE);
        }
    }
}
