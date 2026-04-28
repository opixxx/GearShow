package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    // ===== Then (응답 검증) =====

    @SuppressWarnings("unchecked")
    @Then("첫 번째 댓글의 작성자 정보가 응답에 포함된다")
    public void 첫_댓글_작성자_정보_포함() {
        Map<String, Object> data = (Map<String, Object>) context.getLastResponse().body().get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("data");
        assertThat(items).as("댓글 목록이 비어있지 않아야 한다").isNotEmpty();

        Map<String, Object> first = items.get(0);
        Map<String, Object> author = (Map<String, Object>) first.get("author");

        assertThat(author).as("응답에 author 객체가 존재해야 한다").isNotNull();
        assertThat(author).containsKey("userId");
        assertThat(author.get("userId")).as("author.userId 는 항상 채워진다").isNotNull();
        assertThat(author).containsKey("nickname");
        assertThat(author.get("nickname")).as("가입자 닉네임이 채워져야 한다").isNotNull();
        // profileImageUrl 은 가입 직후 미설정 상태일 수 있으므로 키 존재만 검증
        assertThat(author).containsKey("profileImageUrl");
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
