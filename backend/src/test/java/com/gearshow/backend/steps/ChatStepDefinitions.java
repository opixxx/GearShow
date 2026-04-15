package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 REST MVP 관련 Cucumber Step Definitions.
 *
 * <p>판매자는 Background에서 설정된 {@code accessToken}/{@code userId}를,
 * 구매자는 {@code buyerAccessToken}/{@code buyerUserId}를 사용한다.</p>
 */
public class ChatStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public ChatStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== Given =====

    @Given("카카오 인가 코드 {string}로 가입한 구매자가 존재한다")
    public void 구매자가_존재한다(String authCode) {
        TestResponse<Map<String, Object>> login = apiClient.post(
                "/api/v1/auth/login/kakao",
                Map.of("authorizationCode", authCode));
        assertThat(login.statusCode()).isEqualTo(200);

        Map<String, Object> data = extractData(login);
        String buyerAccessToken = data.get("accessToken").toString();
        context.put("buyerAccessToken", buyerAccessToken);

        apiClient.authenticate(buyerAccessToken);
        TestResponse<Map<String, Object>> me = apiClient.get("/api/v1/users/me");
        apiClient.clearAuth();
        assertThat(me.statusCode()).isEqualTo(200);
        Map<String, Object> meData = extractData(me);
        context.put("buyerUserId", ((Number) meData.get("userId")).longValue());
    }

    @Given("구매자가 쇼케이스 채팅방을 이미 생성했다")
    public void 구매자가_채팅방을_이미_생성했다() {
        Long showcaseId = context.get("showcaseId");
        String token = context.get("buyerAccessToken");
        apiClient.authenticate(token);
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/chat-rooms", Map.of("showcaseId", showcaseId));
        apiClient.clearAuth();
        assertThat(response.statusCode()).isIn(200, 201);
        Map<String, Object> data = extractData(response);
        context.put("chatRoomId", ((Number) data.get("chatRoomId")).longValue());
    }

    @And("구매자가 {string} 메시지를 전송했다")
    public void 구매자가_메시지를_전송했다(String content) {
        Long roomId = context.get("chatRoomId");
        String token = context.get("buyerAccessToken");
        apiClient.authenticate(token);
        Map<String, Object> body = new HashMap<>();
        body.put("messageType", "TEXT");
        body.put("content", content);
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/chat-rooms/" + roomId + "/messages", body);
        apiClient.clearAuth();
        assertThat(response.statusCode()).isEqualTo(201);
        Map<String, Object> data = extractData(response);
        context.put("lastChatMessageId", ((Number) data.get("chatMessageId")).longValue());
    }

    // ===== When =====

    @When("구매자가 쇼케이스 채팅방 생성을 요청한다")
    public void 구매자가_채팅방_생성_요청() {
        Long showcaseId = context.get("showcaseId");
        String token = context.get("buyerAccessToken");
        apiClient.authenticate(token);
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/chat-rooms", Map.of("showcaseId", showcaseId));
        apiClient.clearAuth();
        context.setLastResponse(response);

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            Map<String, Object> data = extractData(response);
            context.put("chatRoomId", ((Number) data.get("chatRoomId")).longValue());
        }
    }

    @When("판매자가 자기 쇼케이스 채팅방 생성을 요청한다")
    public void 판매자가_자기_쇼케이스_채팅방_생성_요청() {
        Long showcaseId = context.get("showcaseId");
        String token = context.get("accessToken");
        apiClient.authenticate(token);
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/chat-rooms", Map.of("showcaseId", showcaseId));
        apiClient.clearAuth();
        context.setLastResponse(response);
    }

    @When("인증 없이 쇼케이스 채팅방 생성을 요청한다")
    public void 인증_없이_채팅방_생성_요청() {
        apiClient.clearAuth();
        Long showcaseId = context.get("showcaseId");
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/chat-rooms", Map.of("showcaseId", showcaseId));
        context.setLastResponse(response);
    }

    @When("구매자가 방금 보낸 메시지를 삭제한다")
    public void 구매자가_방금_보낸_메시지를_삭제한다() {
        Long roomId = context.get("chatRoomId");
        Long messageId = context.get("lastChatMessageId");
        String token = context.get("buyerAccessToken");
        apiClient.authenticate(token);
        TestResponse<Map<String, Object>> response = apiClient.delete(
                "/api/v1/chat-rooms/" + roomId + "/messages/" + messageId);
        apiClient.clearAuth();
        context.setLastResponse(response);
    }

    @When("구매자가 채팅방 메시지 목록을 조회한다")
    public void 구매자가_메시지_목록_조회() {
        Long roomId = context.get("chatRoomId");
        String token = context.get("buyerAccessToken");
        apiClient.authenticate(token);
        TestResponse<Map<String, Object>> response = apiClient.get(
                "/api/v1/chat-rooms/" + roomId + "/messages");
        apiClient.clearAuth();
        context.setLastResponse(response);
    }

    @When("구매자가 2001자 메시지를 전송한다")
    public void 구매자가_과장된_메시지_전송() {
        Long roomId = context.get("chatRoomId");
        String token = context.get("buyerAccessToken");
        apiClient.authenticate(token);
        String content = "가".repeat(2001);
        Map<String, Object> body = new HashMap<>();
        body.put("messageType", "TEXT");
        body.put("content", content);
        TestResponse<Map<String, Object>> response = apiClient.post(
                "/api/v1/chat-rooms/" + roomId + "/messages", body);
        apiClient.clearAuth();
        context.setLastResponse(response);
    }

    // ===== Then =====

    @And("응답의 code는 {string}이다")
    public void 응답_code_확인(String expectedCode) {
        Object code = context.getLastResponse().body().get("code");
        assertThat(code).isNotNull();
        assertThat(code.toString()).isEqualTo(expectedCode);
    }

    @And("메시지 목록 첫 번째 항목의 content는 {string}이다")
    public void 메시지_목록_첫번째_content(String expected) {
        Map<String, Object> data = extractData(context.getLastResponse());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("data");
        assertThat(items).isNotEmpty();
        assertThat(items.get(0).get("content").toString()).isEqualTo(expected);
    }

    // ===== Helper =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(TestResponse<Map<String, Object>> response) {
        Map<String, Object> body = response.body();
        return (Map<String, Object>) body.get("data");
    }
}
