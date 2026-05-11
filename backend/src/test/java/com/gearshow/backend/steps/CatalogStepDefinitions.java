package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
                "uniformSpec", Map.of(
                        "clubName", "Liverpool",
                        "season", "2024-25",
                        "league", "EPL",
                        "kitType", "HOME"
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

    @When("카탈로그 아이템 목록을 카테고리 {string}로 조회한다")
    public void 카탈로그_목록_카테고리_조회(String category) {
        context.setLastResponse(apiClient.get("/api/v1/catalogs?category=" + category));
    }

    @When("카탈로그 아이템 목록을 키워드 {string}로 조회한다")
    public void 카탈로그_목록_키워드_조회(String keyword) {
        context.setLastResponse(apiClient.get(
                "/api/v1/catalogs?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)));
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
                "brand", "Nike"
        );
        context.setLastResponse(apiClient.post("/api/v1/catalogs", request));
    }

    // ===== ADR-016 — 한국어 alias / 빈티지 / 정정 =====

    @Given("한국어 alias 를 포함한 축구화 카탈로그 아이템을 등록한다")
    public void 한국어_alias_축구화_카탈로그가_등록되어_있다() {
        한국어_alias_축구화_등록_수행();
    }

    @When("한국어 alias 를 포함한 축구화 카탈로그 아이템 등록을 요청한다")
    public void 한국어_alias_축구화_등록_요청() {
        한국어_alias_축구화_등록_수행();
    }

    @When("빈티지 유니폼 카탈로그 아이템 등록을 요청한다")
    public void 빈티지_유니폼_등록_요청() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        // ADR-016: kitType 미명시 (빈티지) 케이스
        Map<String, Object> request = Map.of(
                "category", "UNIFORM",
                "brand", "Adidas",
                "modelCode", "VINTAGE-MUFC-" + System.currentTimeMillis(),
                "uniformSpec", Map.of(
                        "clubName", "Manchester United",
                        "clubNameKo", "맨체스터 유나이티드",
                        "season", "1988/90",
                        "league", "EPL"
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

    @When("등록된 카탈로그 아이템의 한국어 풀네임을 {string}로 정정한다")
    public void 한국어_풀네임_정정(String newFullNameKo) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long catalogItemId = context.get("catalogItemId");
        context.setLastResponse(apiClient.patch(
                "/api/v1/catalogs/" + catalogItemId,
                Map.of("fullNameKo", newFullNameKo)));
        apiClient.clearAuth();
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

    /**
     * ADR-016: 한국어 alias 를 포함한 축구화 카탈로그 등록 공통 로직.
     * Given/When 양쪽에서 재사용한다.
     */
    private void 한국어_alias_축구화_등록_수행() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Map<String, Object> request = Map.of(
                "category", "BOOTS",
                "brand", "Nike",
                "modelCode", "AT5889-" + System.currentTimeMillis(),
                "fullNameKo", "나이키 머큐리얼 슈퍼플라이",
                "fullNameEn", "Nike Mercurial Superfly",
                "bootsSpec", Map.of(
                        "studType", "MG",
                        "siloName", "Mercurial Superfly",
                        "siloNameKo", "머큐리얼 슈퍼플라이",
                        "releaseYear", "2024",
                        "surfaceType", "MG"
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
