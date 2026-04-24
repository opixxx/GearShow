package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseOutcome;
import com.gearshow.backend.showcase.application.exception.TripoUnavailableException;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link CreateShowcaseFacade} 의 Tripo Circuit Breaker 사전 차단 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CreateShowcaseFacade — Tripo Circuit Guard")
class CreateShowcaseFacadeCircuitGuardTest {

    private static final List<String> IMAGE_KEYS = List.of("k1.jpg");
    private static final List<String> MODEL_SOURCE_KEYS =
            List.of("s1.jpg", "s2.jpg", "s3.jpg", "s4.jpg");

    @Mock
    private CreateShowcaseService createShowcaseService;
    @Mock
    private ImageStoragePort imageStoragePort;
    @Mock
    private RequestModelGenerationUseCase requestModelGenerationUseCase;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private TripoCircuitGuard tripoCircuitGuard;
    private CreateShowcaseFacade facade;

    @BeforeEach
    void setUp() {
        // 실 CircuitBreaker 인스턴스를 쓰되 임계값을 테스트에서 조작한다.
        circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build());
        tripoCircuitGuard = new TripoCircuitGuard(circuitBreakerRegistry);
        facade = new CreateShowcaseFacade(
                createShowcaseService, imageStoragePort,
                requestModelGenerationUseCase, tripoCircuitGuard);
        given(imageStoragePort.exists(any())).willReturn(true);
        given(imageStoragePort.toUrl(any())).willAnswer(inv -> "https://cdn/" + inv.getArgument(0));
    }

    private CreateShowcaseCommand commandWithModelSource(boolean hasModelSource) {
        return new CreateShowcaseCommand(
                1L, null, Category.BOOTS, "Nike", null,
                "제목", null, null,
                ConditionGrade.A, 0, false, 0, hasModelSource,
                null, null, null,
                UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Circuit OPEN + modelSourceImages 있음 → TripoUnavailableException(503) — DB 저장 호출 안 함")
    void circuitOpen_withModelSource_rejects() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tripo");
        cb.transitionToOpenState();

        assertThatThrownBy(() ->
                facade.create(commandWithModelSource(true), IMAGE_KEYS, MODEL_SOURCE_KEYS))
                .isInstanceOf(TripoUnavailableException.class);

        verify(createShowcaseService, never())
                .saveShowcaseWithSpec(any(CreateShowcaseCommand.class), any());
    }

    @Test
    @DisplayName("Circuit OPEN + modelSourceImages 없음 → 통과 (Tripo 와 무관한 2D 등록)")
    void circuitOpen_withoutModelSource_passes() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tripo");
        cb.transitionToOpenState();

        // createShowcaseService 통과까지 흐르는지만 확인 — 실제 Showcase 도메인 생성은 Mock.
        Showcase mockShowcase = Showcase.builder().id(1L).build();
        given(createShowcaseService.saveShowcaseWithSpec(any(), any()))
                .willReturn(new CreateShowcaseOutcome.Created(mockShowcase));

        // modelSourceImages 빈 목록 → Circuit 검사 통과 + 이후 흐름 진행 (3D 요청 없음).
        facade.create(commandWithModelSource(false), IMAGE_KEYS, List.of());

        verify(requestModelGenerationUseCase, never())
                .requestOnCreate(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Circuit CLOSED → 통과 (차단 없음)")
    void circuitClosed_passes() {
        // 기본 상태가 CLOSED. modelSourceImages 있어도 통과.
        Showcase mockShowcase = Showcase.builder().id(1L).build();
        given(createShowcaseService.saveShowcaseWithSpec(any(), any()))
                .willReturn(new CreateShowcaseOutcome.Created(mockShowcase));

        // requestModelGenerationUseCase 는 modelSourceImageKeys 가 있으므로 호출됨 — 스텁하되 무시.
        given(requestModelGenerationUseCase.requestOnCreate(anyLong(), any(), any()))
                .willReturn(new com.gearshow.backend.showcase.application.dto.ModelGenerationResult(
                        1L, com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus.GENERATING));

        facade.create(commandWithModelSource(true), IMAGE_KEYS, MODEL_SOURCE_KEYS);

        verify(requestModelGenerationUseCase).requestOnCreate(anyLong(), any(), any());
    }
}
