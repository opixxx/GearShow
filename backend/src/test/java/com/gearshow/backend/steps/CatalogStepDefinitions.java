package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;

/**
 * 카탈로그 관련 Cucumber Step Definitions.
 * Given: 사전 조건 (카탈로그가 이미 존재하는 상태)
 * When: 테스트 대상 행위
 * Then: AuthStepDefinitions의 공통 Then 재사용
 */
public class CatalogStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public CatalogStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== Given (사전 조건) =====

    @Given("축구화 카탈로그 아이템을 등록한다")
    public void 축구화_카탈로그가_등록되어_있다() {
        축구화_카탈로그_등록_수행();
    }

    // ===== When (테스트 행위) =====

    @When("축구화 카탈로그 아이템 등록을 요청한다")
    public void 축구화_카탈로그_등록_요청() {
        축구화_카탈로그_등록_수행();
    }

    @When("유니폼 카탈로그 아이템 등록을 요청한다")
    public void 유니폼_카탈로그_등록_요청() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Map<String, Object> request = Map.of(
                "category", "UNIFORM",
                "brand", "Nike",
                "itemName", "Liverpool 24-25 Home Kit",
                "uniformSpec", Map.of(
                        "clubName", "Liverpool",
                        "season", "2024-25",
                        "league", "EPL"
                )
        );

        context.setLastResponse(apiClient.post("/api/v1/catalogs", request));
        apiClient.clearAuth();
    }

    @When("등록된 카탈로그 아이템 상세를 조회한다")
    public void 카탈로그_상세_조회() {
        Long catalogItemId = context.get("catalogItemId");
        context.setLastResponse(apiClient.get("/api/v1/catalogs/" + catalogItemId));
    }

    @When("존재하지 않는 카탈로그 아이템 ID {int}로 조회한다")
    public void 존재하지_않는_카탈로그_조회(int id) {
        context.setLastResponse(apiClient.get("/api/v1/catalogs/" + id));
    }

    @When("카탈로그 아이템 목록을 조회한다")
    public void 카탈로그_목록_조회() {
        context.setLastResponse(apiClient.get("/api/v1/catalogs"));
    }

    @When("등록된 카탈로그 아이템의 브랜드를 {string}로 수정한다")
    public void 카탈로그_브랜드_수정(String newBrand) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long catalogItemId = context.get("catalogItemId");
        context.setLastResponse(apiClient.patch(
                "/api/v1/catalogs/" + catalogItemId,
                Map.of("brand", newBrand)));
        apiClient.clearAuth();
    }

    @When("인증 없이 축구화 카탈로그 아이템을 등록한다")
    public void 인증_없이_카탈로그_등록() {
        apiClient.clearAuth();
        Map<String, Object> request = Map.of(
                "category", "BOOTS",
                "brand", "Nike",
                "itemName", "Test Item"
        );
        context.setLastResponse(apiClient.post("/api/v1/catalogs", request));
    }

    // ===== Helper =====

    /**
     * 축구화 카탈로그 등록 공통 로직.
     * Given/When 모두에서 재사용한다.
     */
    private void 축구화_카탈로그_등록_수행() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Map<String, Object> request = Map.of(
                "category", "BOOTS",
                "brand", "Nike",
                "itemName", "Mercurial Superfly 10 Elite",
                "modelCode", "DJ2839-" + System.currentTimeMillis(),
                "bootsSpec", Map.of(
                        "studType", "FG",
                        "siloName", "Mercurial",
                        "releaseYear", "2025",
                        "surfaceType", "천연잔디"
                )
        );

        TestResponse<Map<String, Object>> response = apiClient.post("/api/v1/catalogs", request);
        context.setLastResponse(response);
        apiClient.clearAuth();

        if (response.statusCode() == 201) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            context.put("catalogItemId", ((Number) data.get("catalogItemId")).longValue());
        }
    }
}
