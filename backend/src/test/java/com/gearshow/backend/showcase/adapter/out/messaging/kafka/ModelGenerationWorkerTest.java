package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
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
 * {@link ModelGenerationWorker} 단위 테스트 (P1-D-α+β).
 *
 * <p>검증 포인트:</p>
 * <ul>
 *   <li>멱등성 가드에서 이미 처리된 메시지 → UseCase 호출 없이 즉시 반환</li>
 *   <li>정상 경로 → {@link PrepareWorkflowUseCase#prepare(Long)} 위임</li>
 *   <li>UseCase 예외 → release() 후 rethrow (설계 결정 #5, 본 PR 범위 내 Tripo 호출 없음)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ModelGenerationWorkerTest {

    @Mock
    private PrepareWorkflowUseCase prepareWorkflowUseCase;

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
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                    "msg-id-1", 11L, 1L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(message.messageId(),
                    IdempotencyDomain.SHOWCASE_MODEL_GENERATION)).willReturn(false);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, never()).prepare(anyLong());
            verify(acquireIdempotencyUseCase, never()).release(any(), any());
        }
    }

    @Nested
    @DisplayName("정상 처리 경로")
    class HappyPath {

        @Test
        @DisplayName("처음 보는 메시지는 prepare(workflowId) 로 위임한다")
        void processModelGeneration_newMessage_delegatesToUseCase() {
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                    "msg-id-5", 15L, 5L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(any(), any())).willReturn(true);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, times(1)).prepare(15L);
            verify(acquireIdempotencyUseCase, never()).release(any(), any());
        }
    }

    @Nested
    @DisplayName("인프라 예외")
    class InfraException {

        @Test
        @DisplayName("UseCase 가 예외를 던지면 release 후 그대로 전파한다 (TX1 실패)")
        void processModelGeneration_useCaseThrows_releasesAndRethrows() {
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                    "msg-id-5", 15L, 5L, 100L);
            given(acquireIdempotencyUseCase.tryAcquire(any(), any())).willReturn(true);
            willThrow(new QueryTimeoutException("DB 일시 장애"))
                    .given(prepareWorkflowUseCase).prepare(15L);

            assertThatThrownBy(() -> worker.processModelGeneration(message))
                    .isInstanceOf(QueryTimeoutException.class);

            verify(acquireIdempotencyUseCase, times(1)).release(
                    message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
        }
    }
}
