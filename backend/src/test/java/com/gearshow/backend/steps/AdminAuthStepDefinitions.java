package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;

/**
 * 관리자 인증/RBAC Cucumber Step Definitions.
 *
 * <p>{@code application-test.yml} 의 {@code app.bootstrap.admin.{email,password}}
 * 가 부트스트랩한 admin 으로 로그인하여 ADMIN 토큰을 ScenarioContext 에 저장한다.</p>
 */
public class AdminAuthStepDefinitions {

    private static final String BOOTSTRAP_EMAIL = "cucumber-admin@test.gearshow.com";
    private static final String BOOTSTRAP_PASSWORD = "cucumber-admin-pw-1234";

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public AdminAuthStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    @Given("관리자가 로그인되어 있다")
    public void 관리자가_로그인되어_있다() {
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/admin/auth/login",
                Map.of("email", BOOTSTRAP_EMAIL, "password", BOOTSTRAP_PASSWORD));

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "관리자 부트스트랩 실패 — application-test.yml 의 app.bootstrap.admin 설정 확인 필요. "
                            + "응답: " + response.body());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.body().get("data");
        context.put("adminAccessToken", data.get("accessToken").toString());
        context.put("adminId", ((Number) data.get("adminId")).longValue());
    }

    @When("관리자 로그인을 잘못된 비밀번호로 요청한다")
    public void 관리자_로그인_잘못된_비밀번호() {
        context.setLastResponse(apiClient.post(
                "/api/admin/auth/login",
                Map.of("email", BOOTSTRAP_EMAIL, "password", "wrong-pw")));
    }

    @When("관리자 로그인을 미존재 이메일로 요청한다")
    public void 관리자_로그인_미존재_이메일() {
        context.setLastResponse(apiClient.post(
                "/api/admin/auth/login",
                Map.of("email", "missing@gearshow.com", "password", "any")));
    }

    @When("정상 관리자 로그인을 요청한다")
    public void 정상_관리자_로그인() {
        context.setLastResponse(apiClient.post(
                "/api/admin/auth/login",
                Map.of("email", BOOTSTRAP_EMAIL, "password", BOOTSTRAP_PASSWORD)));
    }
}
