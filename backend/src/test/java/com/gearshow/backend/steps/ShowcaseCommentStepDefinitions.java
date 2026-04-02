package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;

/**
 * 쇼케이스 댓글 관련 Cucumber Step Definitions.
 * Given: 사전 조건 (댓글이 이미 존재하는 상태)
 * When: 테스트 대상 행위
 * Then: AuthStepDefinitions의 공통 Then 재사용
 */
public class ShowcaseCommentStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public ShowcaseCommentStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== Given (사전 조건) =====

    @Given("쇼케이스에 {string} 댓글이 등록되어 있다")
    public void 댓글이_등록되어_있다(String content) {
        댓글_작성_수행(content);
    }

    // ===== When (테스트 행위) =====

    @When("쇼케이스에 {string} 댓글을 작성한다")
    public void 댓글을_작성한다(String content) {
        댓글_작성_수행(content);
    }

    @When("쇼케이스 댓글 목록을 조회한다")
    public void 댓글_목록_조회() {
        Long showcaseId = context.get("showcaseId");
        context.setLastResponse(apiClient.get(
                "/api/v1/showcases/" + showcaseId + "/comments"));
    }

    @When("등록된 댓글을 {string}로 수정한다")
    public void 댓글_수정(String newContent) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        Long commentId = context.get("commentId");
        context.setLastResponse(apiClient.patch(
                "/api/v1/showcases/" + showcaseId + "/comments/" + commentId,
                Map.of("content", newContent)));
        apiClient.clearAuth();
    }

    @When("등록된 댓글을 삭제한다")
    public void 댓글_삭제() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        Long commentId = context.get("commentId");
        context.setLastResponse(apiClient.delete(
                "/api/v1/showcases/" + showcaseId + "/comments/" + commentId));
        apiClient.clearAuth();
    }

    @When("인증 없이 댓글을 작성한다")
    public void 인증_없이_댓글_작성() {
        apiClient.clearAuth();
        Long showcaseId = context.get("showcaseId");
        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/" + showcaseId + "/comments",
                Map.of("content", "테스트 댓글")));
    }

    // ===== Helper =====

    /**
     * 댓글 작성 공통 로직.
     * Given/When 모두에서 재사용한다.
     */
    private void 댓글_작성_수행(String content) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/showcases/" + showcaseId + "/comments",
                Map.of("content", content));
        context.setLastResponse(response);
        apiClient.clearAuth();

        if (response.statusCode() == 201) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            context.put("commentId", ((Number) data.get("showcaseCommentId")).longValue());
        }
    }
}
