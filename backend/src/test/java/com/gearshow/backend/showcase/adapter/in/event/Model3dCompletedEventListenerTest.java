package com.gearshow.backend.showcase.adapter.in.event;

import com.gearshow.backend.showcase.application.event.Model3dCompletedEvent;
import com.gearshow.backend.showcase.application.port.in.MarkShowcaseHas3dModelUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link Model3dCompletedEventListener} 단위 테스트.
 *
 * <p>{@code @TransactionalEventListener}/{@code @Async} 어노테이션 동작은 Spring 컨텍스트가
 * 있어야 의미가 있으므로 단위 테스트에서는 메서드 호출/예외 분기/메트릭 증가만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Model3dCompletedEventListener")
class Model3dCompletedEventListenerTest {

    private static final Long SHOWCASE_ID = 9_002L;
    private static final String FAILURE_COUNTER = "showcase.has3dmodel.sync.failure";

    @Mock
    private MarkShowcaseHas3dModelUseCase markShowcaseHas3dModelUseCase;

    private MeterRegistry meterRegistry;
    private Model3dCompletedEventListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new Model3dCompletedEventListener(markShowcaseHas3dModelUseCase, meterRegistry);
    }

    @Test
    @DisplayName("Happy: 이벤트의 showcaseId 로 UseCase.markHas3dModel 호출, 메트릭 변화 없음")
    void delegatesToUseCase() {
        listener.onCompleted(new Model3dCompletedEvent(SHOWCASE_ID));

        verify(markShowcaseHas3dModelUseCase, times(1)).markHas3dModel(SHOWCASE_ID);
        assertThat(meterRegistry.counter(FAILURE_COUNTER).count()).isZero();
    }

    @Test
    @DisplayName("DataAccessException: 흡수 + 실패 카운터 1 증가 (호출자로 전파 안 됨)")
    void swallowsDataAccessExceptionAndIncrementsCounter() {
        willThrow(new DataAccessResourceFailureException("DB 연결 끊김"))
                .given(markShowcaseHas3dModelUseCase).markHas3dModel(SHOWCASE_ID);

        assertThatCode(() -> listener.onCompleted(new Model3dCompletedEvent(SHOWCASE_ID)))
                .doesNotThrowAnyException();

        verify(markShowcaseHas3dModelUseCase, times(1)).markHas3dModel(SHOWCASE_ID);
        assertThat(meterRegistry.counter(FAILURE_COUNTER).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("프로그래밍 결함 (DataAccessException 외): 전파 — fail-loud")
    void propagatesNonDataAccessRuntimeException() {
        willThrow(new IllegalStateException("프로그래밍 버그"))
                .given(markShowcaseHas3dModelUseCase).markHas3dModel(SHOWCASE_ID);

        assertThatThrownBy(() -> listener.onCompleted(new Model3dCompletedEvent(SHOWCASE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("프로그래밍 버그");

        // fail-loud 케이스는 메트릭에 누적하지 않는다 — Async 핸들러가 ERROR 로그로 노출
        assertThat(meterRegistry.counter(FAILURE_COUNTER).count()).isZero();
    }
}
