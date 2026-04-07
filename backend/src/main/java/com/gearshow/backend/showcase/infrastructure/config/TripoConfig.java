package com.gearshow.backend.showcase.infrastructure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Tripo 3D 모델 생성 API 설정.
 * tripo.enabled=true일 때만 활성화된다.
 */
@Getter
@Configuration
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "true")
public class TripoConfig {

    @Value("${tripo.api-key}")
    private String apiKey;

    @Value("${tripo.base-url}")
    private String baseUrl;

    @Value("${tripo.poll-interval-ms}")
    private long pollIntervalMs;

    @Value("${tripo.timeout-ms}")
    private long timeoutMs;

    @Value("${tripo.model-version}")
    private String modelVersion;

    /**
     * Tripo API 전용 RestClient Bean.
     * Base URL과 Bearer 인증 헤더가 설정된다.
     */
    @Bean
    public RestClient tripoRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}
