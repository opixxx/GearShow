package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ProcessModelGenerationService 단위 테스트.
 * 비즈니스 흐름(조회 → 상태 전환 → 외부 호출 → 결과 저장)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ProcessModelGenerationServiceTest {

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ShowcasePort showcasePort;

    @Mock
    private ModelGenerationClient modelGenerationClient;

    @InjectMocks
    private ProcessModelGenerationService service;

    private static final Long SHOWCASE_3D_MODEL_ID = 5L;
    private static final Long SHOWCASE_ID = 100L;

    @Nested
    @DisplayName("정상 처리")
    class HappyPath {

        @Test
        @DisplayName("외부 클라이언트 성공 시 COMPLETED 저장 + has3dModel = true 동기화")
        void process_clientSuccess_savesCompleted() {
            // Given
            Showcase3dModel requested = Showcase3dModel.request(SHOWCASE_ID, "tripo");
            Showcase3dModel generating = requested.startGenerating();

            given(showcase3dModelPort.findById(SHOWCASE_3D_MODEL_ID))
                    .willReturn(Optional.of(requested));
            given(showcase3dModelPort.save(any(Showcase3dModel.class))).willReturn(generating);
            given(modelGenerationClient.generate(any(), eq(SHOWCASE_ID)))
                    .willReturn(GenerationResult.success("https://cdn/m.glb", "https://cdn/p.png"));

            // When
            service.process(SHOWCASE_3D_MODEL_ID, SHOWCASE_ID);

            // Then: GENERATING + COMPLETED → save 2회, has3dModel true 동기화
            verify(showcase3dModelPort, times(2)).save(any(Showcase3dModel.class));
            verify(showcasePort).updateHas3dModel(SHOWCASE_ID, true);
        }

        @Test
        @DisplayName("외부 클라이언트 실패 응답 시 FAILED 저장 + has3dModel = false 동기화")
        void process_clientFailure_savesFailed() {
            // Given
            Showcase3dModel requested = Showcase3dModel.request(SHOWCASE_ID, "tripo");
            Showcase3dModel generating = requested.startGenerating();

            given(showcase3dModelPort.findById(SHOWCASE_3D_MODEL_ID))
                    .willReturn(Optional.of(requested));
            given(showcase3dModelPort.save(any(Showcase3dModel.class))).willReturn(generating);
            given(modelGenerationClient.generate(any(), eq(SHOWCASE_ID)))
                    .willReturn(GenerationResult.failure("크레딧 부족"));

            // When
            service.process(SHOWCASE_3D_MODEL_ID, SHOWCASE_ID);

            // Then
            verify(showcasePort).updateHas3dModel(SHOWCASE_ID, false);
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("모델을 찾을 수 없으면 외부 클라이언트를 호출하지 않는다")
        void process_modelNotFound_doesNotCallClient() {
            // Given
            given(showcase3dModelPort.findById(SHOWCASE_3D_MODEL_ID)).willReturn(Optional.empty());

            // When
            service.process(SHOWCASE_3D_MODEL_ID, SHOWCASE_ID);

            // Then
            verify(modelGenerationClient, never()).generate(anyLong(), anyLong());
            verify(showcasePort, never()).updateHas3dModel(anyLong(), eq(false));
        }

        @Test
        @DisplayName("외부 클라이언트가 RestClientException을 던지면 FAILED로 저장한다")
        void process_clientThrowsRestClientException_savesFailed() {
            // Given
            Showcase3dModel requested = Showcase3dModel.request(SHOWCASE_ID, "tripo");
            Showcase3dModel generating = requested.startGenerating();

            given(showcase3dModelPort.findById(SHOWCASE_3D_MODEL_ID))
                    .willReturn(Optional.of(requested));
            given(showcase3dModelPort.save(any(Showcase3dModel.class))).willReturn(generating);
            given(modelGenerationClient.generate(any(), eq(SHOWCASE_ID)))
                    .willThrow(new ResourceAccessException("connection refused"));

            // When
            service.process(SHOWCASE_3D_MODEL_ID, SHOWCASE_ID);

            // Then
            verify(showcasePort).updateHas3dModel(SHOWCASE_ID, false);
        }

        @Test
        @DisplayName("외부 클라이언트가 DataAccessException을 던지면 FAILED로 저장한다")
        void process_clientThrowsDataAccessException_savesFailed() {
            // Given
            Showcase3dModel requested = Showcase3dModel.request(SHOWCASE_ID, "tripo");
            Showcase3dModel generating = requested.startGenerating();

            given(showcase3dModelPort.findById(SHOWCASE_3D_MODEL_ID))
                    .willReturn(Optional.of(requested));
            given(showcase3dModelPort.save(any(Showcase3dModel.class))).willReturn(generating);
            given(modelGenerationClient.generate(any(), eq(SHOWCASE_ID)))
                    .willThrow(new QueryTimeoutException("query timeout"));

            // When
            service.process(SHOWCASE_3D_MODEL_ID, SHOWCASE_ID);

            // Then
            verify(showcasePort).updateHas3dModel(SHOWCASE_ID, false);
        }
    }
}
