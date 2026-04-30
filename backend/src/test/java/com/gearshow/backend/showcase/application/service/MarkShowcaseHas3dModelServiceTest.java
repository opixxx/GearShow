package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link MarkShowcaseHas3dModelService} 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarkShowcaseHas3dModelService")
class MarkShowcaseHas3dModelServiceTest {

    private static final Long SHOWCASE_ID = 9_001L;

    @Mock
    private ShowcasePort showcasePort;

    @InjectMocks
    private MarkShowcaseHas3dModelService service;

    @Test
    @DisplayName("Happy: affected=1 → ShowcasePort.updateHas3dModel(showcaseId, true) 호출, 정상 종료")
    void delegatesToPortWithTrue() {
        given(showcasePort.updateHas3dModel(SHOWCASE_ID, true)).willReturn(1);

        service.markHas3dModel(SHOWCASE_ID);

        verify(showcasePort, times(1)).updateHas3dModel(SHOWCASE_ID, true);
        verifyNoMoreInteractions(showcasePort);
    }

    @Test
    @DisplayName("정합성 이상: affected=0 → 예외 없이 종료 (WARN 로그만)")
    void affectedZero_doesNotThrow() {
        given(showcasePort.updateHas3dModel(SHOWCASE_ID, true)).willReturn(0);

        assertThatCode(() -> service.markHas3dModel(SHOWCASE_ID))
                .doesNotThrowAnyException();
        verify(showcasePort, times(1)).updateHas3dModel(SHOWCASE_ID, true);
    }
}
