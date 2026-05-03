package com.gearshow.backend.admin;

import com.gearshow.backend.admin.adapter.out.persistence.AdminJpaRepository;
import com.gearshow.backend.support.TestOAuthConfig;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 RBAC 통합 테스트.
 *
 * <p>핵심 검증:
 * <ul>
 *   <li>{@code ApplicationStartedEvent} 시 환경변수 기반 admin 부트스트랩이 동작한다</li>
 *   <li>{@code POST /api/admin/auth/login} 정상/실패 시나리오</li>
 *   <li>{@code /api/admin/**} 가드 — admin 토큰 200 / USER 토큰 403 / 토큰 없음 401</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
class AdminAuthIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "admin-bootstrap@test.gearshow.com";
    private static final String BOOTSTRAP_PASSWORD = "test-bootstrap-pw-1234";

    @DynamicPropertySource
    static void registerBootstrapProperties(DynamicPropertyRegistry registry) {
        registry.add("app.bootstrap.admin.email", () -> BOOTSTRAP_EMAIL);
        registry.add("app.bootstrap.admin.password", () -> BOOTSTRAP_PASSWORD);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AdminJpaRepository adminJpaRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("부트스트랩")
    class Bootstrap {

        @Test
        @DisplayName("환경변수가 채워진 상태로 시작하면 admins 테이블에 1건 INSERT 된다")
        void bootstrap_inserts() {
            assertThat(adminJpaRepository.existsByEmail(BOOTSTRAP_EMAIL)).isTrue();
        }
    }

    @Nested
    @DisplayName("로그인 — POST /api/admin/auth/login")
    class Login {

        @Test
        @DisplayName("정상 로그인 시 200 + accessToken 반환, JWT 의 type claim 은 ADMIN")
        void login_success() {
            // When
            ResponseEntity<Map<String, Object>> response = postLogin(BOOTSTRAP_EMAIL, BOOTSTRAP_PASSWORD);

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            String accessToken = (String) data.get("accessToken");
            assertThat(accessToken).isNotBlank();
            assertThat(jwtTokenProvider.getType(accessToken)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("잘못된 password 시 401 ADMIN_INVALID_CREDENTIALS")
        void login_wrongPassword_returns401() {
            ResponseEntity<Map<String, Object>> response = postLogin(BOOTSTRAP_EMAIL, "wrong-pw");
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody().get("code")).isEqualTo("ADMIN_INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("미존재 email 시에도 401 ADMIN_INVALID_CREDENTIALS (user enumeration 방지)")
        void login_unknownEmail_returns401_sameAsWrongPassword() {
            ResponseEntity<Map<String, Object>> response = postLogin("nope@gearshow.com", "any");
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody().get("code")).isEqualTo("ADMIN_INVALID_CREDENTIALS");
        }
    }

    @Nested
    @DisplayName("RBAC 가드 — /api/admin/catalog/bulk-import (PR #71)")
    class RbacGuard {

        @Test
        @DisplayName("admin 토큰으로 호출 시 200")
        void bulkImport_withAdminToken_returns200() {
            String adminToken = loginAndGetAccessToken();
            ResponseEntity<Map<String, Object>> response = postBulkImport(adminToken);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        @DisplayName("일반 USER 토큰으로 호출 시 403")
        void bulkImport_withUserToken_returns403() {
            String userToken = jwtTokenProvider.generateAccessToken(999L, JwtTokenProvider.TYPE_USER);
            ResponseEntity<Map<String, Object>> response = postBulkImport(userToken);
            assertThat(response.getStatusCode().value()).isEqualTo(403);
        }

        @Test
        @DisplayName("토큰 없이 호출 시 401")
        void bulkImport_withoutToken_returns401() {
            ResponseEntity<Map<String, Object>> response = postBulkImport(null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }

        private String loginAndGetAccessToken() {
            ResponseEntity<Map<String, Object>> response = postLogin(BOOTSTRAP_EMAIL, BOOTSTRAP_PASSWORD);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            return (String) data.get("accessToken");
        }
    }

    private ResponseEntity<Map<String, Object>> postLogin(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("email", email, "password", password);
        return restTemplate.exchange(
                "/api/admin/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<Map<String, Object>> postBulkImport(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        Map<String, Object> body = Map.of("items", List.of(
                Map.of(
                        "category", "BOOTS",
                        "brand", "Nike",
                        "modelCode", "ADMIN-RBAC-" + System.currentTimeMillis(),
                        "bootsSpec", Map.of(
                                "studType", "FG",
                                "siloName", "Mercurial",
                                "releaseYear", "2025",
                                "surfaceType", "천연잔디"
                        )
                )
        ));
        return restTemplate.exchange(
                "/api/admin/catalog/bulk-import",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );
    }
}
