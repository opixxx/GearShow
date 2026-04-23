package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link RequestModelGenerationService} 단위 테스트.
 *
 * <p>P1-B-γ 불변식:</p>
 * <ul>
 *   <li>{@code saveModelAndSourceImages} 는 workflow 를 {@code attempt_no=1} 로 INSERT.</li>
 *   <li>{@code resetOrCreateModelAndSaveSourceImages} 는 {@code nextAttemptNo} 로 재시도 순번을 계산.</li>
 *   <li>Publisher 는 결정적 파생을 위해 {@code idempotencyKey} 를 그대로 전달받는다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestModelGenerationService")
class RequestModelGenerationServiceTest {

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ModelSourceImagePort modelSourceImagePort;

    @Mock
    private ModelGenerationWorkflowPort modelGenerationWorkflowPort;

    @Mock
    private ModelGenerationEventPublisher modelGenerationEventPublisher;

    @InjectMocks
    private RequestModelGenerationService service;

    @BeforeEach
    void setUp() {
        given(showcase3dModelPort.save(any(Showcase3dModel.class)))
                .willAnswer(inv -> {
                    Showcase3dModel given = inv.getArgument(0);
                    return cloneWithId(given, 500L);
                });
    }

    private Showcase3dModel cloneWithId(Showcase3dModel given, long newId) {
        return Showcase3dModel.builder()
                .id(newId)
                .showcaseId(given.getShowcaseId())
                .modelStatus(given.getModelStatus())
                .generationProvider(given.getGenerationProvider())
                .requestedAt(given.getRequestedAt() != null ? given.getRequestedAt() : Instant.now())
                .createdAt(Instant.now())
                .retryCount(given.getRetryCount())
                .build();
    }

    @Nested
    @DisplayName("신규 생성 경로 (saveModelAndSourceImages)")
    class OnCreate {

        @Test
        @DisplayName("workflow 를 attempt_no=1 로 INSERT 하고 Publisher 에 동일 idempotencyKey 를 전달한다")
        void saves_workflowWithAttemptOneAndPublishes() {
            given(modelGenerationWorkflowPort.saveRequested(eq(77L), eq("idem-key"), eq(1)))
                    .willReturn(900L);

            service.saveModelAndSourceImages(77L, "idem-key", List.of("url-1", "url-2", "url-3", "url-4"));

            verify(modelGenerationWorkflowPort, times(1)).saveRequested(77L, "idem-key", 1);
            verify(modelGenerationWorkflowPort, never()).nextAttemptNo(anyLong());
            verify(modelGenerationEventPublisher, times(1))
                    .publishRequested(eq(900L), eq(500L), eq(77L), eq("idem-key"));
            verify(modelSourceImagePort, times(1)).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("재시도 경로 (resetOrCreateModelAndSaveSourceImages)")
    class OnRetry {

        @Test
        @DisplayName("기존 모델이 있으면 reset 후 nextAttemptNo 로 새 workflow 를 INSERT 한다")
        void existingModel_usesNextAttemptNo() {
            Showcase3dModel existing = Showcase3dModel.builder()
                    .id(500L)
                    .showcaseId(88L)
                    .modelStatus(ModelStatus.FAILED)
                    .generationProvider("fake-tripo")
                    .requestedAt(Instant.now().minusSeconds(3600))
                    .createdAt(Instant.now().minusSeconds(3600))
                    .retryCount(1)
                    .build();
            given(showcase3dModelPort.findByShowcaseId(88L)).willReturn(Optional.of(existing));
            given(modelGenerationWorkflowPort.nextAttemptNo(88L)).willReturn(3);
            given(modelGenerationWorkflowPort.saveRequested(eq(88L), eq("retry-key"), eq(3)))
                    .willReturn(901L);

            service.resetOrCreateModelAndSaveSourceImages(
                    88L, "retry-key", List.of("a", "b", "c", "d"));

            verify(modelGenerationWorkflowPort, times(1)).nextAttemptNo(88L);
            verify(modelGenerationWorkflowPort, times(1)).saveRequested(88L, "retry-key", 3);
            verify(modelGenerationEventPublisher, times(1))
                    .publishRequested(eq(901L), eq(500L), eq(88L), eq("retry-key"));
        }

        @Test
        @DisplayName("기존 모델이 없으면 새 3D 모델과 workflow 를 생성한다 (nextAttemptNo 는 1 을 반환하도록 mock)")
        void noExistingModel_createsNew() {
            given(showcase3dModelPort.findByShowcaseId(99L)).willReturn(Optional.empty());
            given(modelGenerationWorkflowPort.nextAttemptNo(99L)).willReturn(1);
            given(modelGenerationWorkflowPort.saveRequested(eq(99L), eq("new-key"), eq(1)))
                    .willReturn(902L);

            service.resetOrCreateModelAndSaveSourceImages(
                    99L, "new-key", List.of("a", "b", "c", "d"));

            verify(modelGenerationWorkflowPort, times(1)).saveRequested(99L, "new-key", 1);
            verify(modelGenerationEventPublisher, times(1))
                    .publishRequested(eq(902L), eq(500L), eq(99L), eq("new-key"));
        }
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
