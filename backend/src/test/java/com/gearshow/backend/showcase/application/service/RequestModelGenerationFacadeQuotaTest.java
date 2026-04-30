package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.exception.DailyLimitExceededException;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationDailyQuotaPort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationDailyQuotaPort.QuotaResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationDailyQuotaPort.QuotaToken;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link RequestModelGenerationFacade} 의 quota 통합 동작 단위 테스트.
 *
 * <p>검증:</p>
 * <ul>
 *   <li>quota 통과 → 후속 작업 실행</li>
 *   <li>quota 거부 → {@link DailyLimitExceededException} + 후속 작업 미호출</li>
 *   <li>후속 작업 RuntimeException → quota rollback (정확한 token 으로) + 원본 예외 rethrow</li>
 *   <li>후속 작업 정상 → rollback 호출 안 됨</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestModelGenerationFacade quota 통합")
class RequestModelGenerationFacadeQuotaTest {

    private static final Long SHOWCASE_ID = 100L;
    private static final Long OWNER_ID = 42L;
    private static final String IDEMPOTENCY_KEY = "idem-1";
    private static final List<String> SOURCE_KEYS = List.of("k1", "k2", "k3", "k4");

    @Mock
    private RequestModelGenerationService requestModelGenerationService;
    @Mock
    private ShowcasePort showcasePort;
    @Mock
    private ModelGenerationWorkflowPort modelGenerationWorkflowPort;
    @Mock
    private ImageStoragePort imageStoragePort;
    @Mock
    private TripoCircuitGuard tripoCircuitGuard;
    @Mock
    private ModelGenerationDailyQuotaPort dailyQuotaPort;

    private RequestModelGenerationFacade facade;

    @BeforeEach
    void setUp() {
        facade = new RequestModelGenerationFacade(
                requestModelGenerationService, showcasePort, modelGenerationWorkflowPort,
                imageStoragePort, tripoCircuitGuard, dailyQuotaPort);
        given(imageStoragePort.toUrl(anyString())).willAnswer(inv -> "https://cdn/" + inv.getArgument(0));
    }

    private QuotaResult allowedResult() {
        QuotaToken token = new QuotaToken(OWNER_ID,
                LocalDate.now(ZoneId.of("Asia/Seoul")));
        return new QuotaResult(true, 1L, 3L, Instant.now().plusSeconds(60), token);
    }

    private QuotaResult deniedResult() {
        QuotaToken token = new QuotaToken(OWNER_ID,
                LocalDate.now(ZoneId.of("Asia/Seoul")));
        return new QuotaResult(false, 3L, 3L, Instant.now().plusSeconds(60), token);
    }

    @Test
    @DisplayName("quota 통과 → 후속 작업 실행 + rollback 미호출")
    void onCreate_quotaAllowed_runsAction() {
        given(dailyQuotaPort.tryConsume(OWNER_ID)).willReturn(allowedResult());
        given(requestModelGenerationService.saveSourceImagesAndRequest(anyLong(), anyString(), any()))
                .willReturn(new ModelGenerationResult(1L, ShowcaseModelStatus.GENERATING));

        ModelGenerationResult result = facade.requestOnCreate(
                SHOWCASE_ID, OWNER_ID, IDEMPOTENCY_KEY, SOURCE_KEYS);

        assertThat(result.workflowId()).isEqualTo(1L);
        verify(dailyQuotaPort, times(1)).tryConsume(OWNER_ID);
        verify(dailyQuotaPort, never()).rollback(any());
        verify(requestModelGenerationService, times(1))
                .saveSourceImagesAndRequest(eq(SHOWCASE_ID), eq(IDEMPOTENCY_KEY), any());
    }

    @Test
    @DisplayName("quota 거부 → DailyLimitExceededException + 후속 작업 미호출 + rollback 미호출")
    void onCreate_quotaDenied_throwsAndDoesNotInvokeAction() {
        given(dailyQuotaPort.tryConsume(OWNER_ID)).willReturn(deniedResult());

        assertThatThrownBy(() -> facade.requestOnCreate(
                SHOWCASE_ID, OWNER_ID, IDEMPOTENCY_KEY, SOURCE_KEYS))
                .isInstanceOf(DailyLimitExceededException.class);

        verify(requestModelGenerationService, never())
                .saveSourceImagesAndRequest(anyLong(), anyString(), any());
        verify(dailyQuotaPort, never()).rollback(any());
    }

    @Test
    @DisplayName("후속 작업 RuntimeException → quota rollback (정확한 token) 후 원본 예외 rethrow")
    void onCreate_actionThrows_rollsBackQuota() {
        QuotaResult allowed = allowedResult();
        given(dailyQuotaPort.tryConsume(OWNER_ID)).willReturn(allowed);
        RuntimeException workflowFailure = new IllegalStateException("workflow boom");
        given(requestModelGenerationService.saveSourceImagesAndRequest(anyLong(), anyString(), any()))
                .willThrow(workflowFailure);

        assertThatThrownBy(() -> facade.requestOnCreate(
                SHOWCASE_ID, OWNER_ID, IDEMPOTENCY_KEY, SOURCE_KEYS))
                .isSameAs(workflowFailure);

        // tryConsume 이 발급한 token 을 그대로 rollback 에 전달해야 함 (자정 race 방어).
        verify(dailyQuotaPort, times(1)).rollback(allowed.token());
    }

    @Test
    @DisplayName("requestRetry: quota 거부 → workflow 재시도 미발생")
    void retry_quotaDenied_skipsWorkflow() {
        // Showcase 검증, validateNotAlreadyGenerating, circuitGuard 모두 통과시키기 위한 stub
        com.gearshow.backend.showcase.domain.model.Showcase showcase =
                com.gearshow.backend.showcase.domain.model.Showcase.builder()
                        .id(SHOWCASE_ID).ownerId(OWNER_ID).build();
        given(showcasePort.findById(SHOWCASE_ID)).willReturn(java.util.Optional.of(showcase));
        given(modelGenerationWorkflowPort.findLatestSnapshotByShowcaseId(SHOWCASE_ID))
                .willReturn(java.util.Optional.empty());
        given(dailyQuotaPort.tryConsume(OWNER_ID)).willReturn(deniedResult());

        assertThatThrownBy(() -> facade.requestRetry(
                SHOWCASE_ID, OWNER_ID, UUID.randomUUID().toString(), SOURCE_KEYS))
                .isInstanceOf(DailyLimitExceededException.class);

        verify(requestModelGenerationService, never())
                .resetSourceImagesAndRequestRetry(anyLong(), anyString(), any());
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
