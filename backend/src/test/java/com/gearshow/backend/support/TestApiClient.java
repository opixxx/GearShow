package com.gearshow.backend.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

/**
 * Cucumber Step Definition에서 사용하는 재사용 가능한 HTTP 클라이언트 추상화.
 * TestRestTemplate을 래핑하여 TestResponse를 반환함으로써,
 * 테스트 코드가 Spring HTTP 내부 구현에 강결합되지 않도록 한다.
 *
 * <p>사용 예시:
 * <pre>
 *   TestResponse<Map<String, Object>> response = apiClient.get("/api/v1/health");
 *   assertThat(response.statusCode()).isEqualTo(200);
 * </pre>
 */
@Component
public class TestApiClient {

    private final TestRestTemplate restTemplate;
    private String authToken;

    public TestApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 인증이 필요한 요청을 위해 Bearer 토큰을 설정한다.
     * null을 전달하면 인증을 해제한다.
     */
    public void authenticate(String token) {
        this.authToken = token;
    }

    /**
     * 현재 설정된 인증 토큰을 제거한다.
     */
    public void clearAuth() {
        this.authToken = null;
    }

    /**
     * GET 요청을 보내고 응답을 Map으로 반환한다.
     *
     * @param path API 경로 (예: "/api/v1/health")
     * @return 파싱된 응답
     */
    public TestResponse<Map<String, Object>> get(String path) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return TestResponse.from(response);
    }

    /**
     * JSON 본문과 함께 POST 요청을 보내고 응답을 Map으로 반환한다.
     *
     * <p>쇼케이스 POST 엔드포인트는 {@code Idempotency-Key} 헤더를 필수화했다 (ADR-011 ①).
     * 기존 시나리오의 호환을 위해, 이 클라이언트는 해당 엔드포인트 호출 시 UUID 기반 키를
     * 자동으로 주입한다. 명시적으로 키를 지정하려면 {@link #post(String, Object, String)} 를 사용.</p>
     *
     * @param path API 경로
     * @param body 요청 본문 객체 (JSON으로 직렬화됨)
     * @return 파싱된 응답
     */
    public TestResponse<Map<String, Object>> post(String path, Object body) {
        String autoKey = requiresAutoIdempotencyKey(path) ? UUID.randomUUID().toString() : null;
        return post(path, body, autoKey);
    }

    /**
     * JSON 본문과 명시적 {@code Idempotency-Key} 를 함께 보낸다. {@code idempotencyKey} 가
     * {@code null} 이면 헤더를 생략한다.
     */
    public TestResponse<Map<String, Object>> post(String path, Object body, String idempotencyKey) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );
        return TestResponse.from(response);
    }

    private boolean requiresAutoIdempotencyKey(String path) {
        if (path == null) {
            return false;
        }
        // POST /api/v1/showcases : 쇼케이스 등록
        if ("/api/v1/showcases".equals(path)) {
            return true;
        }
        // POST /api/v1/showcases/{id}/3d-model : 3D 모델 재요청
        return path.startsWith("/api/v1/showcases/") && path.endsWith("/3d-model");
    }

    /**
     * JSON 본문과 함께 PATCH 요청을 보내고 응답을 Map으로 반환한다.
     *
     * @param path API 경로
     * @param body 요청 본문 객체
     * @return 파싱된 응답
     */
    public TestResponse<Map<String, Object>> patch(String path, Object body) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path,
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );
        return TestResponse.from(response);
    }

    /**
     * DELETE 요청을 보내고 응답을 Map으로 반환한다.
     *
     * @param path API 경로
     * @return 파싱된 응답
     */
    public TestResponse<Map<String, Object>> delete(String path) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path,
                HttpMethod.DELETE,
                new HttpEntity<>(buildHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return TestResponse.from(response);
    }

    /**
     * Multipart form-data POST 요청을 보낸다.
     *
     * @param path  API 경로
     * @param parts multipart 파트 (key-value)
     * @return 파싱된 응답
     */
    public TestResponse<Map<String, Object>> postMultipart(String path, MultiValueMap<String, Object> parts) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path,
                HttpMethod.POST,
                new HttpEntity<>(parts, headers),
                new ParameterizedTypeReference<>() {}
        );
        return TestResponse.from(response);
    }

    /**
     * Multipart form-data PATCH 요청을 보낸다.
     *
     * @param path  API 경로
     * @param parts multipart 파트 (key-value)
     * @return 파싱된 응답
     */
    public TestResponse<Map<String, Object>> patchMultipart(String path, MultiValueMap<String, Object> parts) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path,
                HttpMethod.PATCH,
                new HttpEntity<>(parts, headers),
                new ParameterizedTypeReference<>() {}
        );
        return TestResponse.from(response);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (authToken != null) {
            headers.setBearerAuth(authToken);
        }
        return headers;
    }
}
