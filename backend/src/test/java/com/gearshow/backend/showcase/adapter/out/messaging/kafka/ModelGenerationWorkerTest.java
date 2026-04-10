package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.ProcessModelGenerationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ModelGenerationWorker 단위 테스트.
 *
 * <p>Worker는 어댑터 책임(트리거 + 멱등성 + 보상 삭제)만 담당하므로
 * 비즈니스 로직 위임과 멱등성 흐름만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ModelGenerationWorkerTest {

    @Mock
    private ProcessModelGenerationUseCase processModelGenerationUseCase;

    @Mock
    private AcquireIdempotencyUseCase acquireIdempotencyUseCase;

    @InjectMocks
    private ModelGenerationWorker worker;

    @Nested
    @DisplayName("멱등성 가드")
    class IdempotencyGuardBehavior {

        @Test
        @DisplayName("이미 처리된 메시지면 비즈니스 유스케이스를 호출하지 않는다")
        void processModelGeneration_duplicateMessage_skipsUseCase() {
            // Given
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(1L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(message.messageId(),
                    IdempotencyDomain.SHOWCASE_MODEL_GENERATION)).willReturn(false);

            // When
            worker.processModelGeneration(message);

            // Then
            verify(processModelGenerationUseCase, never()).process(anyLong(), anyLong());
            verify(acquireIdempotencyUseCase, never()).release(any(), any());
        }
    }

    @Nested
    @DisplayName("정상 처리 경로")
    class HappyPath {

        @Test
        @DisplayName("처음 보는 메시지는 비즈니스 유스케이스에 위임한다")
        void processModelGeneration_newMessage_delegatesToUseCase() {
            // Given
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(5L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(any(), any())).willReturn(true);

            // When
            worker.processModelGeneration(message);

            // Then
            verify(processModelGenerationUseCase, times(1)).process(5L, 100L);
            verify(acquireIdempotencyUseCase, never()).release(any(), any());
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("비즈니스 유스케이스 예외 발생 시 멱등성 선점을 해제하고 예외를 재전파한다")
        void processModelGeneration_useCaseThrows_releasesIdempotencyAndRethrows() {
            // Given
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(5L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(any(), any())).willReturn(true);
            willThrow(new QueryTimeoutException("DB 일시 장애"))
                    .given(processModelGenerationUseCase).process(5L, 100L);

            // When & Then
            assertThatThrownBy(() -> worker.processModelGeneration(message))
                    .isInstanceOf(QueryTimeoutException.class);

            verify(acquireIdempotencyUseCase).release(
                    message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
        }
    }
}
