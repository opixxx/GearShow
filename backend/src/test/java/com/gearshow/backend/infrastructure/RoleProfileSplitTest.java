package com.gearshow.backend.infrastructure;

import com.gearshow.backend.platform.idempotency.adapter.in.scheduler.ProcessedMessageCleanupScheduler;
import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import com.gearshow.backend.platform.outbox.adapter.in.scheduler.OutboxCleanupScheduler;
import com.gearshow.backend.platform.outbox.adapter.in.scheduler.OutboxRelayScheduler;
import com.gearshow.backend.platform.outbox.application.port.in.CleanupOutboxUseCase;
import com.gearshow.backend.platform.outbox.application.port.in.PublishOutboxUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할(api/worker) 분리 스캐폴딩 검증 (ADR-027, PR1).
 *
 * <p>실제 {@code application.yml} + {@code application-{profile}.yml} 를
 * {@link ConfigDataApplicationContextInitializer} 로 로딩하여 (1) 프로파일 번들이 올바른
 * 플래그를 set 하는지, (2) 신규 조건부 어노테이션이 의도대로 빈을 등록/비등록하는지,
 * (3) F1 스케줄러 풀이 적용되는지를 검증한다. {@code test} 프로파일은
 * {@code spring.kafka.enabled=false} + KafkaAutoConfiguration exclude 라 kafka-게이트 빈을
 * 관측할 수 없으므로, Testcontainers 풀 컨텍스트 대신 {@link ApplicationContextRunner} 로
 * 프로퍼티/조건만 격리 검증한다 (kafka·redis 인프라 불필요).</p>
 *
 * <p><b>운영 무회귀가 본 테스트의 헤드라인</b>: api/worker 프로파일 *미적용* 시
 * 신규 토글이 전부 활성 = 현 모놀리스와 동일 — {@code prod} 베이스라인으로 증명한다.</p>
 */
@DisplayName("역할 분리 스캐폴딩 (ADR-027)")
class RoleProfileSplitTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. 운영 무회귀 가드 (헤드라인) — api/worker 프로파일 미적용 = 현 모놀리스
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("무회귀 가드 — prod 베이스라인(api/worker 프로파일 없음) → 신규 토글 전부 활성")
    void 무회귀가드_prod_프로파일만이면_역할토글_전부_활성() {
        // Given — 운영 현행과 동일하게 prod 프로파일만 (api/worker 미포함)
        runner().withPropertyValues("spring.profiles.active=prod")
                // When — 컨텍스트 환경 평가
                .run(ctx -> {
                    // Then — 신규 역할 토글이 전부 기본 활성(true) = 분리 전 모놀리스 동작 유지
                    assertThat(ctx.getEnvironment().getProperty("app.outbox.relay-enabled"))
                            .as("Outbox Relay 는 prod 에서 활성이어야 함(무회귀)")
                            .isEqualTo("true");
                    assertThat(ctx.getEnvironment().getProperty("app.outbox.cleanup-enabled"))
                            .isEqualTo("true");
                    assertThat(ctx.getEnvironment().getProperty("app.idempotency.cleanup-enabled"))
                            .isEqualTo("true");
                    assertThat(ctx.getEnvironment()
                            .getProperty("app.model-generation.worker-enabled"))
                            .as("3D Consumer 는 prod 에서 활성이어야 함(무회귀)")
                            .isEqualTo("true");
                });
    }

    @Test
    @DisplayName("F1 — spring.task.scheduling.pool.size=3 가 적용된다")
    void F1_스케줄러_풀크기_3() {
        // Given — 어떤 역할 프로파일과도 무관한 전역 설정(application.yml)
        runner().withPropertyValues("spring.profiles.active=prod")
                // When
                .run(ctx ->
                        // Then — 단일 스레드(core=1) 직렬화로 인한 스케줄러 기아(F1) 해소
                        assertThat(ctx.getEnvironment()
                                .getProperty("spring.task.scheduling.pool.size"))
                                .isEqualTo("3"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. 프로파일 번들 정확성 — api / worker 가 올바른 쪽을 비활성화
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("api 프로파일 — 3D worker 컴포넌트(Consumer/Drainer/Reconcile/Poller) 비활성")
    void api프로파일_3D컴포넌트_비활성() {
        // Given — prod 위에 api 프로파일 적용 (목록 뒤가 우선 → api 가 prod override)
        runner().withPropertyValues("spring.profiles.active=prod,api")
                // When
                .run(ctx -> {
                    // Then — api 는 HTTP+공용 인프라만, 3D 무거운 작업은 전부 off
                    assertThat(ctx.getEnvironment()
                            .getProperty("app.model-generation.worker-enabled"))
                            .isEqualTo("false");
                    assertThat(ctx.getEnvironment()
                            .getProperty("app.model-generation.admission.enabled"))
                            .isEqualTo("false");
                    assertThat(ctx.getEnvironment().getProperty("app.reconcile.enabled"))
                            .as("api 가 application-prod.yml 의 reconcile.enabled=true 를 덮어써야 함")
                            .isEqualTo("false");
                    assertThat(ctx.getEnvironment()
                            .getProperty("app.workflow-polling.scheduler-enabled"))
                            .isEqualTo("false");
                    // 공용 인프라는 api 가 계속 소유 = 활성 유지
                    assertThat(ctx.getEnvironment().getProperty("app.outbox.relay-enabled"))
                            .isEqualTo("true");
                });
    }

    @Test
    @DisplayName("worker 프로파일 — 공용 인프라(Relay/정리 배치) 비활성, 3D 는 유지")
    void worker프로파일_공용인프라_비활성() {
        // Given — prod 위에 worker 프로파일 적용
        runner().withPropertyValues("spring.profiles.active=prod,worker")
                // When
                .run(ctx -> {
                    // Then — 공용 인프라는 api 단독 소유 → worker 에서 off (2-프로세스 중복 차단)
                    assertThat(ctx.getEnvironment().getProperty("app.outbox.relay-enabled"))
                            .isEqualTo("false");
                    assertThat(ctx.getEnvironment().getProperty("app.outbox.cleanup-enabled"))
                            .isEqualTo("false");
                    assertThat(ctx.getEnvironment().getProperty("app.idempotency.cleanup-enabled"))
                            .isEqualTo("false");
                    // 3D Consumer 는 worker 가 소유 → prod 기본(true) 유지(over-specify 안 함)
                    assertThat(ctx.getEnvironment()
                            .getProperty("app.model-generation.worker-enabled"))
                            .isEqualTo("true");
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. 신규 조건부 어노테이션이 실제로 빈을 등록/비등록 (kafka 불필요한 빈으로 검증)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OutboxCleanupScheduler — cleanup-enabled=false 면 비등록, 미설정이면 등록")
    void cleanup스케줄러_조건부등록() {
        ApplicationContextRunner base = runner()
                .withBean(CleanupOutboxUseCase.class,
                        () -> Mockito.mock(CleanupOutboxUseCase.class))
                .withUserConfiguration(OutboxCleanupScheduler.class);

        // Given/When/Then — 미설정(matchIfMissing=true) → 등록 (무회귀: prod 가 이 경로)
        base.run(ctx -> assertThat(ctx).hasSingleBean(OutboxCleanupScheduler.class));

        // Given/When/Then — worker 가 false 로 끄면 → 비등록
        base.withPropertyValues("app.outbox.cleanup-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxCleanupScheduler.class));
    }

    @Test
    @DisplayName("ProcessedMessageCleanupScheduler — idempotency.cleanup-enabled=false 면 비등록")
    void idempotency정리스케줄러_조건부등록() {
        ApplicationContextRunner base = runner()
                .withBean(CleanupProcessedMessageUseCase.class,
                        () -> Mockito.mock(CleanupProcessedMessageUseCase.class))
                .withUserConfiguration(ProcessedMessageCleanupScheduler.class);

        base.run(ctx -> assertThat(ctx).hasSingleBean(ProcessedMessageCleanupScheduler.class));

        base.withPropertyValues("app.idempotency.cleanup-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ProcessedMessageCleanupScheduler.class));
    }

    @Test
    @DisplayName("OutboxRelayScheduler — @ConditionalOnExpression(kafka AND relay) 변환 동등성")
    void outboxRelay_조건식_변환_동등성() {
        ApplicationContextRunner base = runner()
                .withBean(PublishOutboxUseCase.class,
                        () -> Mockito.mock(PublishOutboxUseCase.class))
                .withUserConfiguration(OutboxRelayScheduler.class);

        // Then 1 — kafka on + relay 미설정 → 등록 (기존 @ConditionalOnProperty(kafka) 와 동등)
        base.withPropertyValues("spring.kafka.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(OutboxRelayScheduler.class));

        // Then 2 — kafka on + relay=false(worker) → 비등록 (신규 역할 게이트)
        base.withPropertyValues("spring.kafka.enabled=true", "app.outbox.relay-enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxRelayScheduler.class));

        // Then 3 — kafka off → 비등록 (변환 전 동작 보존: 기존 kafka-off 테스트 무회귀)
        base.withPropertyValues("spring.kafka.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxRelayScheduler.class));
    }
}
