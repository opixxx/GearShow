package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.PrepareModelGenerationUseCase;
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
 * <p><b>설계 결정 #5 (멱등성 레코드 release 규칙)</b> 반영:</p>
 * <ul>
 *   <li>UseCase 가 예외를 던지면 → Tripo 호출 전 실패 → release() 호출 후 예외 전파</li>
 *   <li>UseCase 가 정상 반환하면 → release 없음 (성공 또는 UseCase 내부에서 처리)</li>
 *   <li>멱등성 가드에서 이미 처리된 메시지 → release 호출 없이 즉시 반환</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ModelGenerationWorkerTest {

    @Mock
    private PrepareModelGenerationUseCase prepareModelGenerationUseCase;

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
            verify(prepareModelGenerationUseCase, never()).prepare(anyLong(), anyLong());
            // release() 는 폴링 분리 아키텍처에서 완전히 제거되었다
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
            verify(prepareModelGenerationUseCase, times(1)).prepare(5L, 100L);
            verify(acquireIdempotencyUseCase, never()).release(any(), any());
        }
    }

    @Nested
    @DisplayName("인프라 예외")
    class InfraException {

        @Test
        @DisplayName("UseCase 가 예외를 던지면 release 후 그대로 전파한다 (Tripo 호출 전 실패)")
        void processModelGeneration_useCaseThrows_releasesAndRethrows() {
            // Given
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(5L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(any(), any())).willReturn(true);
            willThrow(new QueryTimeoutException("DB 일시 장애"))
                    .given(prepareModelGenerationUseCase).prepare(5L, 100L);

            // When & Then
            // 예외는 Spring Kafka DefaultErrorHandler → 재시도 → DLT 로 이동
            assertThatThrownBy(() -> worker.processModelGeneration(message))
                    .isInstanceOf(QueryTimeoutException.class);

            // [설계 결정 #5] 예외가 Worker 까지 전파 = Tripo 호출 전 실패
            // → release() 호출하여 재시도 시 tryAcquire 가 다시 성공하도록 허용
            verify(acquireIdempotencyUseCase, times(1)).release(
                    message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
        }
    }
}
