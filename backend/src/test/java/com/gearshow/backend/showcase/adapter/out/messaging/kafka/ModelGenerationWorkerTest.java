package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ModelGenerationWorker 단위 테스트.
 * 멱등성 가드의 동작이 비즈니스 로직에 정확히 영향을 주는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ModelGenerationWorkerTest {

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ShowcasePort showcasePort;

    @Mock
    private ModelGenerationClient modelGenerationClient;

    @Mock
    private AcquireIdempotencyUseCase acquireIdempotencyUseCase;

    @InjectMocks
    private ModelGenerationWorker worker;

    @Test
    @DisplayName("이미 처리된 메시지면 비즈니스 로직을 실행하지 않는다")
    void processModelGeneration_duplicateMessage_skipsBusinessLogic() {
        // Given
        ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(1L, 100L);
        given(acquireIdempotencyUseCase.tryAcquire(message.messageId(),
                IdempotencyDomain.SHOWCASE_MODEL_GENERATION)).willReturn(false);

        // When
        worker.processModelGeneration(message);

        // Then
        verify(acquireIdempotencyUseCase).tryAcquire(message.messageId(),
                IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
        verify(showcase3dModelPort, never()).findById(anyLong());
        verify(modelGenerationClient, never()).generate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("처음 보는 메시지에서 모델을 찾을 수 없으면 외부 클라이언트를 호출하지 않는다")
    void processModelGeneration_modelNotFound_doesNotCallClient() {
        // Given
        ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(999L, 100L);
        given(acquireIdempotencyUseCase.tryAcquire(any(), any())).willReturn(true);
        given(showcase3dModelPort.findById(999L)).willReturn(java.util.Optional.empty());

        // When
        worker.processModelGeneration(message);

        // Then
        verify(modelGenerationClient, never()).generate(anyLong(), anyLong());
    }
}
