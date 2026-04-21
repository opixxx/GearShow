package com.gearshow.backend.user.infrastructure.config;

import com.gearshow.backend.user.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정.
 *
 * <p>구조:</p>
 * <ul>
 *   <li>{@link #apiSecurityFilterChain} : 메인 포트(8080) — 일반 API 요청을 JWT 기반으로 인가</li>
 *   <li>{@link #actuatorSecurityFilterChain} : 관리 포트(9091) — {@code management.server.port}
 *       가 설정된 운영 환경에서만 활성화되며, 관리 포트 자체가 docker 내부 네트워크에서만
 *       접근 가능하도록 설계되어 인증을 요구하지 않는다.</li>
 * </ul>
 *
 * <p>20줄 SRP 원칙에 따라 각 설정 단계를 별도 헬퍼로 분리했다.</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * WebSocket 엔드포인트 전용 보안 필터 체인.
     *
     * <p>WebSocket 업그레이드 요청은 HTTP 필터를 완전히 우회해야 한다.
     * STOMP CONNECT 시점에 JWT 인증을 별도로 수행한다 (WebSocketAuthInterceptor).</p>
     */
    @Bean
    @Order(0)
    public SecurityFilterChain webSocketSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/ws/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * 메인 애플리케이션 포트(8080) 의 보안 필터 체인.
     *
     * <p>로컬 프로파일에서는 {@code management.server.port} 가 설정되지 않아 actuator 도
     * 8080 에 함께 올라오므로, {@code /actuator/health,info,prometheus} 를 이 체인에서
     * 허용해야 한다. 운영 프로파일에서는 actuator 가 9091 로 분리되어 이 체인의
     * {@code /actuator/*} 매칭은 사실상 비활성 규칙이 된다.</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        configureStatelessCorsAndCsrf(http);
        http.authorizeHttpRequests(this::configureAuthorization);
        configureExceptionHandling(http);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 관리 포트(9091) 전용 보안 필터 체인.
     *
     * <p>활성 조건: {@code management.server.port} 속성이 application-prod.yml 에서
     * 설정된 경우에만 빈이 생성된다. 관리 포트는 docker compose 의 {@code ports:}
     * 매핑에 포함되지 않고 EC2 보안 그룹에도 등록되지 않으므로, 같은 compose 네트워크의
     * Prometheus 컨테이너만 접근 가능하다. 따라서 별도 인증을 요구하지 않는다.</p>
     *
     * <p>외부 공격 면:</p>
     * <ul>
     *   <li>인터넷 → 9091 : 차단 (security group 미등록)</li>
     *   <li>EC2 호스트 → 9091 : 차단 (docker compose 내부 네트워크 전용)</li>
     *   <li>Prometheus 컨테이너 → 9091 : 허용 (동일 compose 네트워크)</li>
     * </ul>
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "management.server.port")
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * CORS, CSRF, 세션 정책 설정.
     *
     * <p>JWT 기반 무상태 인증을 사용하므로 CSRF 를 비활성화하고, 세션을 생성하지 않는다.</p>
     */
    private void configureStatelessCorsAndCsrf(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /**
     * URL 패턴별 인가 규칙 설정.
     *
     * <p>로컬/개발 편의를 위해 actuator 엔드포인트도 여기서 허용한다. 운영에서는
     * actuator 가 9091 로 분리되어 이 규칙은 실질적으로 미사용이다.</p>
     */
    private void configureAuthorization(
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // 정적 리소스 (채팅 테스트 페이지 등)
                .requestMatchers("/chat-test.html").permitAll()
                // Observability — 로컬/개발용. 운영에서는 management.server.port 분리
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // 인증 없이 접근 가능한 엔드포인트
                .requestMatchers("/api/v1/auth/login/**").permitAll()
                .requestMatchers("/api/v1/auth/dev-login").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/catalogs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/showcases/**").permitAll()
                // 닉네임 중복 확인은 인증 없이 접근 가능
                .requestMatchers(HttpMethod.GET, "/api/v1/users/nicknames/check").permitAll()
                // /users/me 및 하위 경로는 인증 필수 (/{userId}보다 먼저 매칭되도록)
                .requestMatchers("/api/v1/users/me/**").authenticated()
                .requestMatchers("/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{userId}").permitAll()
                // 나머지는 인증 필요
                .anyRequest().authenticated();
    }

    /**
     * 인증 실패 시 응답 처리.
     *
     * <p>인증되지 않은 요청에 대해 401 을 반환한다.</p>
     */
    private void configureExceptionHandling(HttpSecurity http) throws Exception {
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
    }

    /**
     * CORS 설정.
     * Flutter 웹(로컬 개발)과 실제 배포 도메인의 요청을 허용한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Flutter 웹 로컬 개발 서버 (flutter run -d chrome 기본 포트)
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.gearshow.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);
        return source;
    }
}
