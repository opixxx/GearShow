package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.MessageIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.AdmissionDrainMessageId;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ModelGenerationWorker} 단위 테스트 (ADR-011 §2.③ 완료 시점 INSERT 패턴).
 *
 * <p>검증 포인트:</p>
 * <ul>
 *   <li>이미 처리 완료된 재전송 메시지 → prepare() 호출 없이 즉시 반환</li>
 *   <li>처음 보는 메시지 → prepare() 위임 후 markProcessed 1회 호출</li>
 *   <li>prepare() 가 retryable 예외 throw → markProcessed 미호출, 예외 재전파</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ModelGenerationWorkerTest {

    @Mock
    private PrepareWorkflowUseCase prepareWorkflowUseCase;

    @Mock
    private MessageIdempotencyUseCase messageIdempotencyUseCase;

    @InjectMocks
    private ModelGenerationWorker worker;

    @Nested
    @DisplayName("멱등성 가드")
    class IdempotencyGuard {

        @Test
        @DisplayName("이미 처리 완료된 메시지면 prepare() 를 호출하지 않고 markProcessed 도 다시 호출하지 않는다")
        void processModelGeneration_alreadyProcessed_skipsPrepareAndMark() {
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                    "msg-id-1", 11L, 100L);
            given(messageIdempotencyUseCase.isProcessed(message.messageId(),
                    IdempotencyDomain.SHOWCASE_MODEL_GENERATION)).willReturn(true);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, never()).prepare(anyLong());
            verify(messageIdempotencyUseCase, never()).markProcessed(any(), any());
        }
    }

    @Nested
    @DisplayName("정상 처리 경로")
    class HappyPath {

        @Test
        @DisplayName("처음 보는 메시지는 prepare() 후 markProcessed 를 1회 호출한다")
        void processModelGeneration_newMessage_delegatesAndMarks() {
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                    "msg-id-5", 15L, 100L);
            given(messageIdempotencyUseCase.isProcessed(any(), any())).willReturn(false);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, times(1)).prepare(15L);
            verify(messageIdempotencyUseCase, times(1)).markProcessed(
                    message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
        }
    }

    @Nested
    @DisplayName("예외 전파 경로")
    class ExceptionPropagation {

        @ParameterizedTest(name = "{0} 발생 시 markProcessed 호출 없이 재전파")
        @ValueSource(classes = {
                QueryTimeoutException.class,           // retryable 인프라 장애
                DataIntegrityViolationException.class, // 비-retryable (즉시 DLT)
                IllegalStateException.class            // 프로그래밍 버그
        })
        @DisplayName("prepare() 가 어떤 예외를 던지든 markProcessed 호출 없이 그대로 재전파한다")
        void processModelGeneration_prepareThrows_skipsMarkAndRethrows(Class<? extends RuntimeException> exceptionType) throws Exception {
            ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                    "msg-id-5", 15L, 100L);
            given(messageIdempotencyUseCase.isProcessed(any(), any())).willReturn(false);
            RuntimeException thrown = exceptionType
                    .getConstructor(String.class)
                    .newInstance("테스트용 예외");
            willThrow(thrown).given(prepareWorkflowUseCase).prepare(15L);

            assertThatThrownBy(() -> worker.processModelGeneration(message))
                    .isInstanceOf(exceptionType);

            verify(messageIdempotencyUseCase, never()).markProcessed(any(), any());
        }
    }

    @Nested
    @DisplayName("입장 큐 라우팅 (ADR-025)")
    class AdmissionRouting {

        @Test
        @DisplayName("bypassAdmission=true(drain id): prepareFromAdmissionQueue(epoch 파싱) 위임, prepare 미호출")
        void bypassTrue_routesToFromAdmissionQueue() {
            String drainId = AdmissionDrainMessageId.build(15L, 1234L);
            ModelGenerationRequestMessage message =
                    ModelGenerationRequestMessage.ofDrain(drainId, 15L, 100L);
            given(messageIdempotencyUseCase.isProcessed(any(), any())).willReturn(false);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, times(1)).prepareFromAdmissionQueue(15L, 1234L);
            verify(prepareWorkflowUseCase, never()).prepare(anyLong());
            verify(messageIdempotencyUseCase, times(1)).markProcessed(
                    drainId, IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
        }

        @Test
        @DisplayName("bypassAdmission=true 이나 messageId 형식 불일치: now() 대체로 prepareFromAdmissionQueue 위임")
        void bypassTrue_malformedId_fallsBackToNow() {
            ModelGenerationRequestMessage message =
                    ModelGenerationRequestMessage.ofDrain("not-a-drain-id", 15L, 100L);
            given(messageIdempotencyUseCase.isProcessed(any(), any())).willReturn(false);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, times(1)).prepareFromAdmissionQueue(eq(15L), anyLong());
            verify(prepareWorkflowUseCase, never()).prepare(anyLong());
        }

        @Test
        @DisplayName("bypassAdmission=false(of): prepare 위임, prepareFromAdmissionQueue 미호출")
        void bypassFalse_routesToPrepare() {
            ModelGenerationRequestMessage message =
                    ModelGenerationRequestMessage.of("orig-msg-id", 15L, 100L);
            given(messageIdempotencyUseCase.isProcessed(any(), any())).willReturn(false);

            worker.processModelGeneration(message);

            verify(prepareWorkflowUseCase, times(1)).prepare(15L);
            verify(prepareWorkflowUseCase, never()).prepareFromAdmissionQueue(anyLong(), anyLong());
        }
    }
}
