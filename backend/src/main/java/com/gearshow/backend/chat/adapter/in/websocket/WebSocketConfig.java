package com.gearshow.backend.chat.adapter.in.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 설정 (api-spec §8-6).
 *
 * <p>Phase 1: SimpleBroker(인메모리) 단일 서버. Phase 3 에서 Kafka 외부 브로커 전환 예정.</p>
 *
 * <h3>Origin 허용 정책</h3>
 * <p>{@code gearshow.websocket.allowed-origins} 프로퍼티로 주입한다. 로컬 개발은 기본값
 * {@code http://localhost:*} 만 허용하고, prod 프로파일은 실제 도메인으로 엄격히 override 한다.
 * 와일드카드 {@code *} 는 CSRF 유사 공격 경로를 만들어 금지.</p>
 *
 * <h3>HeartBeat</h3>
 * <p>모바일 네트워크 전환(Wi-Fi ↔ LTE) 으로 발생하는 half-open 커넥션을 탐지하기 위해 서버/클라이언트
 * 각 10초 주기의 heartbeat 를 활성화한다.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_SERVER_MS = 10_000L;
    private static final long HEARTBEAT_CLIENT_MS = 10_000L;

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketSubscriptionInterceptor webSocketSubscriptionInterceptor;

    @Value("${gearshow.websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{HEARTBEAT_SERVER_MS, HEARTBEAT_CLIENT_MS})
                .setTaskScheduler(webSocketTaskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor, webSocketSubscriptionInterceptor);
    }

    @Bean
    ThreadPoolTaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
