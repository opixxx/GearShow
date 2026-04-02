package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 비활성화 시 No-op 빈을 제공하는 설정.
 * Kafka가 꺼져있어도 애플리케이션이 정상 기동되도록 한다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpKafkaConfig {

    /**
     * 3D 모델 생성 요청을 무시하는 No-op 구현체.
     */
    @Bean
    public ModelGenerationPort noOpModelGenerationPort() {
        return (showcase3dModelId, showcaseId) ->
                log.warn("Kafka가 비활성화되어 3D 모델 생성 요청이 무시됩니다 - showcase3dModelId: {}, showcaseId: {}",
                        showcase3dModelId, showcaseId);
    }

    /**
     * 3D 모델 생성을 항상 실패 처리하는 No-op 구현체.
     */
    @Bean
    public ModelGenerationClient noOpModelGenerationClient() {
        return (showcase3dModelId, showcaseId) -> {
            log.warn("Kafka가 비활성화되어 3D 모델 생성을 수행할 수 없습니다");
            return ModelGenerationClient.GenerationResult.failure("Kafka가 비활성화되어 3D 모델 생성 불가");
        };
    }
}
