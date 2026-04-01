package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.When;

import java.util.Map;

/**
 * 사용자 프로필 관련 Cucumber Step Definitions.
 */
public class UserStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public UserStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== When =====

    @When("발급받은 Access Token으로 내 프로필을 조회한다")
    public void 내_프로필_조회() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        context.setLastResponse(apiClient.get("/api/v1/users/me"));
        apiClient.clearAuth();
    }

    @When("사용자 ID로 공개 프로필을 조회한다")
    public void 공개_프로필_조회() {
        // Given 단계에서 저장된 응답의 userId 사용
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        TestResponse<Map<String, Object>> meResponse = apiClient.get("/api/v1/users/me");
        apiClient.clearAuth();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) meResponse.body().get("data");
        Integer userId = (Integer) data.get("userId");

        context.setLastResponse(apiClient.get("/api/v1/users/" + userId));
    }

    @When("존재하지 않는 사용자 ID {int}로 프로필을 조회한다")
    public void 존재하지_않는_사용자_프로필_조회(int userId) {
        context.setLastResponse(apiClient.get("/api/v1/users/" + userId));
    }

    @When("인증 없이 내 프로필을 조회한다")
    public void 인증_없이_내_프로필_조회() {
        apiClient.clearAuth();
        context.setLastResponse(apiClient.get("/api/v1/users/me"));
    }

    @When("닉네임을 {string}으로 수정한다")
    public void 닉네임_수정(String newNickname) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        context.setLastResponse(apiClient.patch(
                "/api/v1/users/me",
                Map.of("nickname", newNickname)));
        apiClient.clearAuth();
    }

    @When("발급받은 Access Token으로 회원 탈퇴를 요청한다")
    public void 회원_탈퇴_요청() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        context.setLastResponse(apiClient.delete("/api/v1/users/me"));
        apiClient.clearAuth();
    }

    @When("인증 없이 회원 탈퇴를 요청한다")
    public void 인증_없이_회원_탈퇴() {
        apiClient.clearAuth();
        context.setLastResponse(apiClient.delete("/api/v1/users/me"));
    }

}
