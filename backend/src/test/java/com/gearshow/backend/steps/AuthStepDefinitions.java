package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 관련 Cucumber Step Definitions.
 * 소셜 로그인, 토큰 갱신, 로그아웃 시나리오를 처리한다.
 */
public class AuthStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public AuthStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== Given =====

    @Given("카카오 인가 코드 {string}로 가입한 사용자가 존재한다")
    public void 카카오_사용자가_존재한다(String authCode) {
        // 로그인 수행하여 사용자 자동 가입
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/login/kakao",
                Map.of("authorizationCode", authCode));

        assertThat(response.statusCode()).isEqualTo(200);

        // 발급받은 토큰을 컨텍스트에 저장 (후속 Step에서 사용)
        Map<String, Object> data = extractData(response);
        String accessToken = data.get("accessToken").toString();
        context.put("accessToken", accessToken);
        context.put("refreshToken", data.get("refreshToken").toString());

        // userId도 저장 (프로필 조회 등 후속 Step에서 사용)
        apiClient.authenticate(accessToken);
        TestResponse<Map<String, Object>> meResponse = apiClient.get("/api/v1/users/me");
        apiClient.clearAuth();
        if (meResponse.statusCode() == 200) {
            Map<String, Object> meData = extractData(meResponse);
            context.put("userId", ((Number) meData.get("userId")).longValue());
        }
    }

    // ===== When =====

    @When("카카오 인가 코드 {string}로 로그인을 요청한다")
    public void 카카오_로그인_요청(String authCode) {
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/login/kakao",
                Map.of("authorizationCode", authCode));
        context.setLastResponse(response);
    }

    @When("{string} 인가 코드 {string}로 로그인을 요청한다")
    public void 특정_제공자_로그인_요청(String provider, String authCode) {
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/login/" + provider,
                Map.of("authorizationCode", authCode));
        context.setLastResponse(response);
    }

    @When("발급받은 Refresh Token으로 토큰 갱신을 요청한다")
    public void 발급받은_리프레시_토큰으로_갱신() {
        String refreshToken = context.get("refreshToken");
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
        context.setLastResponse(response);
    }

    @When("Refresh Token {string}으로 토큰 갱신을 요청한다")
    public void 특정_리프레시_토큰으로_갱신(String refreshToken) {
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
        context.setLastResponse(response);
    }

    @When("발급받은 Access Token으로 로그아웃을 요청한다")
    public void 발급받은_액세스_토큰으로_로그아웃() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/logout", null);
        context.setLastResponse(response);
        apiClient.clearAuth();
    }

    @When("인증 없이 로그아웃을 요청한다")
    public void 인증_없이_로그아웃() {
        apiClient.clearAuth();
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/auth/logout", null);
        context.setLastResponse(response);
    }

    // ===== Then =====

    @Then("응답 상태 코드는 {int}이다")
    public void 응답_상태_코드_확인(int expectedStatus) {
        assertThat(context.getLastResponse().statusCode()).isEqualTo(expectedStatus);
    }

    @And("응답의 data에 {string} 필드가 존재한다")
    public void 응답_data_필드_존재_확인(String fieldName) {
        Map<String, Object> data = extractData(context.getLastResponse());
        assertThat(data).containsKey(fieldName);
        assertThat(data.get(fieldName)).isNotNull();
    }

    @And("응답의 data의 {string} 필드는 null이다")
    public void 응답_data_필드_null_확인(String fieldName) {
        Map<String, Object> data = extractData(context.getLastResponse());
        assertThat(data).containsKey(fieldName);
        assertThat(data.get(fieldName)).isNull();
    }

    @And("응답의 data의 {string} 필드는 {string}이다")
    public void 응답_data_필드_값_확인(String fieldName, String expectedValue) {
        Map<String, Object> data = extractData(context.getLastResponse());
        assertThat(data.get(fieldName).toString()).isEqualTo(expectedValue);
    }

    // ===== Helper =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(TestResponse<Map<String, Object>> response) {
        Map<String, Object> body = response.body();
        return (Map<String, Object>) body.get("data");
    }
}
