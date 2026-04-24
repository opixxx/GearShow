package com.gearshow.backend.showcase.adapter.in.event;

import com.gearshow.backend.showcase.application.event.TripoSuccessEvent;
import com.gearshow.backend.showcase.application.port.in.DownloadAndMirrorUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link TripoSuccessEventListener} 단위 테스트.
 *
 * <p>{@code @TransactionalEventListener}·{@code @Async} 어노테이션은 Spring 컨텍스트가 있을 때만
 * 의미가 있으므로 단위 테스트에서는 메서드 호출 자체만 검증한다. 어노테이션 동작은
 * AFTER_COMMIT 보장·Executor 라우팅 이슈 발생 시 통합 테스트로 이어간다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TripoSuccessEventListener")
class TripoSuccessEventListenerTest {

    @Mock
    private DownloadAndMirrorUseCase downloadAndMirrorUseCase;

    @InjectMocks
    private TripoSuccessEventListener listener;

    @Test
    @DisplayName("onTripoSuccess: 이벤트의 workflowId/tripoTaskId 로 UseCase.download 호출")
    void delegatesToUseCase() {
        TripoSuccessEvent event = new TripoSuccessEvent(42L, "tripo-abc");

        listener.onTripoSuccess(event);

        verify(downloadAndMirrorUseCase, times(1)).download(42L, "tripo-abc");
    }
}
