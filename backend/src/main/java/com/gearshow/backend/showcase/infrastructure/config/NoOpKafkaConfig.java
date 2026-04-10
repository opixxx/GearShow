package com.gearshow.backend.showcase.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 비활성화 플래그 감지용 마커 설정.
 *
 * <p>과거에는 {@code ModelGenerationPort} 의 No-op 구현을 제공했으나,
 * Transactional Outbox 패턴 도입 이후 Facade 는 Kafka 계층을 직접 참조하지 않는다.
 * 따라서 이 클래스는 더 이상 빈을 제공하지 않으며, 역사적 맥락 파악과
 * 추후 Kafka 비활성화 관련 대체 빈 등록 포인트로 유지만 한다.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpKafkaConfig {
}
