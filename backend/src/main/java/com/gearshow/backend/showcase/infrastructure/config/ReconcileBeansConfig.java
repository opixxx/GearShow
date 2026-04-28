package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.in.scheduler.ReconcileScheduler;
import com.gearshow.backend.showcase.application.port.in.ReconcileStuckWorkflowsUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.application.service.ReconcileStuckWorkflowsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reconcile 빈 등록 구성 (PR #50 후속, deferred_refactor #18).
 *
 * <p><b>왜 별도 {@link Configuration} 인가</b>: 이전엔 {@link ReconcileStuckWorkflowsService}
 * 와 {@link ReconcileScheduler} 가 직접 {@code @Service}/{@code @Component} +
 * {@code @ConditionalOnExpression("${app.reconcile.enabled:false} and ${gearshow.redis.enabled:false}")}
 * 로 등록됐다. 이로 인해 application 레이어 ({@link ReconcileStuckWorkflowsService}) 가
 * 인프라 키워드 {@code gearshow.redis.enabled} 를 직접 참조하는 헥사고날 누수가 있었다.</p>
 *
 * <p>이 Configuration 은 두 빈을 명시 등록하면서 활성화 조건을 빈 의존성
 * ({@link WorkflowPollQueuePort} 존재) 으로 표현한다. application 코드는 인프라 키워드를
 * 모르고, 활성화 조건은 Spring 표준 어노테이션 두 개로 명확히 표현된다.</p>
 *
 * <p><b>활성화 매트릭스</b> (이전 SpEL AND 와 동일 결과):</p>
 * <table>
 *   <tr><th>{@code app.reconcile.enabled}</th><th>{@code gearshow.redis.enabled}</th>
 *       <th>{@link WorkflowPollQueuePort} 빈</th><th>이 Config</th><th>Reconcile 빈</th></tr>
 *   <tr><td>true</td><td>true</td><td>등록 (Redisson 어댑터)</td><td>활성</td><td>등록</td></tr>
 *   <tr><td>true</td><td>false</td><td>미등록</td>
 *       <td>{@code @ConditionalOnBean} false → 비활성</td><td>미등록</td></tr>
 *   <tr><td>false</td><td>*</td><td>*</td>
 *       <td>{@code @ConditionalOnProperty} false → 비활성</td><td>미등록</td></tr>
 *   <tr><td>미설정</td><td>*</td><td>*</td><td>default false → 비활성</td><td>미등록</td></tr>
 * </table>
 *
 * <p>{@link ReconcileProperties} 는 {@code GearShowApplication} 의
 * {@code @ConfigurationPropertiesScan} 으로 자동 등록되므로 별도
 * {@code @EnableConfigurationProperties} 가 필요 없다.</p>
 *
 * <p><b>옵션 2 (일반 {@code @Configuration}) 채택 사유</b>: Spring Boot 공식 문서가
 * {@code @ConditionalOnBean} 을 {@code @Configuration} 클래스 안에서만 사용 권장하는 이유는
 * 컴포넌트 스캔 처리 순서가 비결정적이라 평가 시점이 불안정할 수 있기 때문이다. 이 우려는
 * 통합 테스트 ({@code ReconcileBeansConfigIntegrationTest}) 로 prod 환경의 진짜 처리 순서에서
 * 시나리오 #4 (두 플래그 모두 활성 → 등록) 가 통과함을 검증하여 해소했다. 만약 향후 어떤 변경
 * (예: {@code RedissonWorkflowPollQueueAdapter} 의 등록 방식 변화) 으로 그 통합 테스트가 깨지면
 * {@code @AutoConfiguration} 으로의 전환을 검토해야 한다.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.reconcile", name = "enabled", havingValue = "true")
@ConditionalOnBean(WorkflowPollQueuePort.class)
public class ReconcileBeansConfig {

    @Bean
    public ReconcileStuckWorkflowsService reconcileStuckWorkflowsService(
            ModelGenerationWorkflowPort workflowPort,
            TripoPendingTaskPort tripoPendingTaskPort,
            WorkflowLockPort workflowLockPort,
            WorkflowPollQueuePort workflowPollQueuePort,
            ApplicationEventPublisher eventPublisher,
            ReconcileProperties properties) {
        return new ReconcileStuckWorkflowsService(
                workflowPort,
                tripoPendingTaskPort,
                workflowLockPort,
                workflowPollQueuePort,
                eventPublisher,
                properties);
    }

    @Bean
    public ReconcileScheduler reconcileScheduler(
            ReconcileStuckWorkflowsUseCase reconcileStuckWorkflowsUseCase) {
        return new ReconcileScheduler(reconcileStuckWorkflowsUseCase);
    }
}
