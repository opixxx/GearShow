package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.When;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 관리자 비밀번호 변경 Cucumber Step Definitions.
 *
 * <p>{@code TestApiClient} 가 PATCH 를 직접 지원하지 않으므로 {@code TestRestTemplate} 을 직접 사용한다.</p>
 */
public class AdminPasswordChangeStepDefinitions {

    private static final String BOOTSTRAP_PASSWORD = "cucumber-admin-pw-1234";
    private static final String BOOTSTRAP_EMAIL = "cucumber-admin@test.gearshow.com";

    private final TestApiClient apiClient;
    private final ScenarioContext context;
    private final TestRestTemplate restTemplate;

    public AdminPasswordChangeStepDefinitions(TestApiClient apiClient,
                                              ScenarioContext context,
                                              TestRestTemplate restTemplate) {
        this.apiClient = apiClient;
        this.context = context;
        this.restTemplate = restTemplate;
    }

    @When("관리자가 새 비밀번호 {string}로 변경 요청한다")
    public void 관리자_새_비밀번호로_변경_요청(String newPassword) {
        sendPasswordChange(BOOTSTRAP_PASSWORD, newPassword);
    }

    @When("관리자가 잘못된 현재 비밀번호로 변경 요청한다")
    public void 관리자_잘못된_현재_비밀번호로_변경_요청() {
        sendPasswordChange("wrong-current-pw", "new-strong-pw-1234");
    }

    @When("관리자가 현재와 동일한 비밀번호로 변경 요청한다")
    public void 관리자_현재와_동일한_비밀번호로_변경_요청() {
        sendPasswordChange(BOOTSTRAP_PASSWORD, BOOTSTRAP_PASSWORD);
    }

    @When("새 비밀번호 {string}로 관리자 로그인을 요청한다")
    public void 새_비밀번호로_로그인_요청(String password) {
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/admin/auth/login",
                Map.of("email", BOOTSTRAP_EMAIL, "password", password));
        context.setLastResponse(response);

        // 정리: 후속 시나리오를 위해 비밀번호 원복 (smoke 시나리오에서만 도달)
        if (response.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            String tokenAfter = data.get("accessToken").toString();
            sendPasswordChangeWithToken(tokenAfter, password, BOOTSTRAP_PASSWORD);
        }
    }

    private void sendPasswordChange(String currentPassword, String newPassword) {
        String adminToken = context.get("adminAccessToken");
        sendPasswordChangeWithToken(adminToken, currentPassword, newPassword);
    }

    private void sendPasswordChangeWithToken(String token, String currentPassword, String newPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        Map<String, Object> body = Map.of(
                "currentPassword", currentPassword,
                "newPassword", newPassword);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/admin/me/password",
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        context.setLastResponse(com.gearshow.backend.support.TestResponse.from(response));
    }
}
