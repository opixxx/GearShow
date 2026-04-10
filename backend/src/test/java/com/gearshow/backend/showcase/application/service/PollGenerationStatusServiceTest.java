package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationStatus;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import com.gearshow.backend.showcase.infrastructure.config.TripoPollingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PollGenerationStatusService 단위 테스트.
 *
 * <p>폴링 분리 아키텍처의 핵심 스케줄러 로직을 검증한다:</p>
 * <ul>
 *   <li>RUNNING → (타임아웃 여부에 따라) markPolled 또는 fail</li>
 *   <li>SUCCESS → fetchResult → complete + has3dModel=true</li>
 *   <li>FAILED → fail + has3dModel=false</li>
 *   <li>한 모델의 예외가 배치 전체에 전파되지 않는 격리 동작</li>
 * </ul>
 *
 * <p>상태 전이 + 쇼케이스 동기화는 {@link ModelGenerationStateWriter} 에 위임되므로
 * 해당 빈을 mock 하고 올바른 호출 여부를 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class PollGenerationStatusServiceTest {

    private static final int BATCH_SIZE = 20;
    private static final int TIMEOUT_MINUTES = 15;
    private static final Long SHOWCASE_ID = 100L;
    private static final String TASK_ID = "tripo-task-xyz";

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ModelGenerationClient modelGenerationClient;

    @Mock
    private ModelGenerationStateWriter stateWriter;

    private PollGenerationStatusService service;

    @BeforeEach
    void setUp() {
        // record 는 mock 할 필요 없이 직접 생성
        TripoPollingProperties properties = new TripoPollingProperties(3_000L, BATCH_SIZE, TIMEOUT_MINUTES);
        service = new PollGenerationStatusService(
                showcase3dModelPort, modelGenerationClient, stateWriter, properties);
    }

    /**
     * 과거 시각의 requestedAt 으로 GENERATING 모델을 생성한다.
     *
     * @param requestedMinutesAgo 현재 시각에서 몇 분 전에 요청되었는지
     */
    private Showcase3dModel generatingModel(Long id, long requestedMinutesAgo) {
        Instant requestedAt = Instant.now().minus(Duration.ofMinutes(requestedMinutesAgo));
        return Showcase3dModel.builder()
                .id(id)
                .showcaseId(SHOWCASE_ID)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider("fake-tripo")
                .generationTaskId(TASK_ID + "-" + id)
                .requestedAt(requestedAt)
                .createdAt(requestedAt)
                .build();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("SUCCESS 상태면 fetchResult 후 stateWriter.markCompleted 에 위임된다")
        void pollOnce_successStatus_delegatesToMarkCompleted() {
            // Given
            Showcase3dModel model = generatingModel(1L, 2);
            GenerationResult result = new GenerationResult("https://cdn/m.glb", "https://cdn/p.jpg");
            given(showcase3dModelPort.findPollableGeneratingTasks(BATCH_SIZE)).willReturn(List.of(model));
            given(modelGenerationClient.fetchStatus(anyString())).willReturn(GenerationStatus.success());
            given(modelGenerationClient.fetchResult(anyString(), eq(SHOWCASE_ID))).willReturn(result);

            // When
            int terminalCount = service.pollOnce();

            // Then
            assertThat(terminalCount).isEqualTo(1);
            verify(stateWriter, times(1)).markCompleted(model, result);
            verify(stateWriter, never()).markFailed(any(), anyString());
            verify(stateWriter, never()).markPolled(any());
        }

        @Test
        @DisplayName("RUNNING + 타임아웃 전이면 stateWriter.markPolled 에 위임되고 카운트는 0")
        void pollOnce_runningNotTimedOut_delegatesToMarkPolled() {
            // Given
            Showcase3dModel model = generatingModel(1L, 2); // 2분 전 → 타임아웃 아님
            given(showcase3dModelPort.findPollableGeneratingTasks(BATCH_SIZE)).willReturn(List.of(model));
            given(modelGenerationClient.fetchStatus(anyString())).willReturn(GenerationStatus.running());

            // When
            int terminalCount = service.pollOnce();

            // Then
            assertThat(terminalCount).isZero();
            verify(stateWriter, times(1)).markPolled(model);
            verify(stateWriter, never()).markCompleted(any(), any());
            verify(stateWriter, never()).markFailed(any(), anyString());
            verify(modelGenerationClient, never()).fetchResult(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("폴링 대상이 없으면 0을 반환하고 Tripo/stateWriter 호출을 시도하지 않는다")
        void pollOnce_noTargets_returnsZero() {
            // Given
            given(showcase3dModelPort.findPollableGeneratingTasks(BATCH_SIZE)).willReturn(List.of());

            // When
            int terminalCount = service.pollOnce();

            // Then
            assertThat(terminalCount).isZero();
            verify(modelGenerationClient, never()).fetchStatus(anyString());
            verify(stateWriter, never()).markPolled(any());
            verify(stateWriter, never()).markCompleted(any(), any());
            verify(stateWriter, never()).markFailed(any(), anyString());
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("RUNNING + 타임아웃이면 stateWriter.markFailed 가 \"시간 초과\" 사유로 호출된다")
        void pollOnce_runningTimedOut_delegatesToMarkFailed() {
            // Given: 타임아웃 기준(15분) 을 넘긴 모델
            Showcase3dModel model = generatingModel(1L, 16);
            given(showcase3dModelPort.findPollableGeneratingTasks(BATCH_SIZE)).willReturn(List.of(model));
            given(modelGenerationClient.fetchStatus(anyString())).willReturn(GenerationStatus.running());

            // When
            int terminalCount = service.pollOnce();

            // Then
            assertThat(terminalCount).isEqualTo(1);
            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(stateWriter, times(1)).markFailed(eq(model), reasonCaptor.capture());
            assertThat(reasonCaptor.getValue()).contains("시간 초과");
            verify(stateWriter, never()).markCompleted(any(), any());
            verify(modelGenerationClient, never()).fetchResult(anyString(), anyLong());
        }

        @Test
        @DisplayName("FAILED 상태면 stateWriter.markFailed 가 Tripo 실패 사유로 호출된다")
        void pollOnce_failedStatus_delegatesToMarkFailedWithReason() {
            // Given
            Showcase3dModel model = generatingModel(1L, 2);
            given(showcase3dModelPort.findPollableGeneratingTasks(BATCH_SIZE)).willReturn(List.of(model));
            given(modelGenerationClient.fetchStatus(anyString()))
                    .willReturn(GenerationStatus.failed("이미지 품질 부족"));

            // When
            int terminalCount = service.pollOnce();

            // Then
            assertThat(terminalCount).isEqualTo(1);
            verify(stateWriter, times(1)).markFailed(model, "이미지 품질 부족");
            verify(stateWriter, never()).markCompleted(any(), any());
        }
    }

    @Nested
    @DisplayName("격리 동작 (배치 안정성)")
    class BatchIsolation {

        @Test
        @DisplayName("한 모델에서 예외가 나도 나머지 모델들은 정상 처리된다")
        void pollOnce_oneModelThrows_othersStillProcessed() {
            // Given
            Showcase3dModel m1 = generatingModel(1L, 2);
            Showcase3dModel m2 = generatingModel(2L, 2);
            Showcase3dModel m3 = generatingModel(3L, 2);
            given(showcase3dModelPort.findPollableGeneratingTasks(BATCH_SIZE))
                    .willReturn(List.of(m1, m2, m3));

            // m1: 정상 SUCCESS
            // m2: fetchStatus 에서 예외
            // m3: 정상 SUCCESS
            given(modelGenerationClient.fetchStatus(m1.getGenerationTaskId()))
                    .willReturn(GenerationStatus.success());
            willThrow(new RuntimeException("Tripo 네트워크 오류"))
                    .given(modelGenerationClient).fetchStatus(m2.getGenerationTaskId());
            given(modelGenerationClient.fetchStatus(m3.getGenerationTaskId()))
                    .willReturn(GenerationStatus.success());

            given(modelGenerationClient.fetchResult(anyString(), eq(SHOWCASE_ID)))
                    .willReturn(new GenerationResult("https://cdn/m.glb", "https://cdn/p.jpg"));

            // When
            int terminalCount = service.pollOnce();

            // Then: 2건만 terminal, m2 는 예외로 스킵되었어도 전체 배치는 계속 진행됨
            assertThat(terminalCount).isEqualTo(2);
            verify(stateWriter, times(2)).markCompleted(any(), any());
            verify(stateWriter, never()).markFailed(any(), anyString());
        }
    }
}
