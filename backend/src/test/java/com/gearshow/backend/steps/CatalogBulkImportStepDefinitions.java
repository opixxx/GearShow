package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카탈로그 아이템 일괄 등록 Cucumber Step Definitions.
 *
 * <p>기존 {@link CatalogStepDefinitions} 와 별도 파일로 분리하여 다른 PR 과의 충돌을 회피한다.
 * Cucumber 가 패키지 내 모든 step 클래스를 자동 스캔하므로 별도 등록은 불필요하다.</p>
 */
public class CatalogBulkImportStepDefinitions {

    private static final String BULK_IMPORT_PATH = "/api/admin/catalog/bulk-import";

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public CatalogBulkImportStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== Given =====

    @And("모델 코드 {string}인 축구화 카탈로그 아이템이 사전 등록되어 있다")
    public void 사전_등록된_축구화(String modelCode) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        apiClient.post("/api/v1/catalogs", buildBootsItem("Nike", modelCode));
        apiClient.clearAuth();
    }

    // ===== When =====

    @When("축구화 1건과 유니폼 1건을 일괄 등록 요청한다")
    public void 축구화_유니폼_각_1건_일괄_등록() {
        Map<String, Object> body = Map.of("items", List.of(
                buildBootsItem("Nike", "BULK-BOOTS-" + System.currentTimeMillis()),
                buildUniformItem("Liverpool", "24-25")
        ));
        sendBulkImport(body);
    }

    @When("모델 코드 {string}인 축구화 1건과 모델 코드 {string}인 축구화 1건을 일괄 등록 요청한다")
    public void 두_축구화_일괄_등록(String dupModelCode, String newModelCode) {
        Map<String, Object> body = Map.of("items", List.of(
                buildBootsItem("Nike", dupModelCode),
                buildBootsItem("Nike", newModelCode)
        ));
        sendBulkImport(body);
    }

    @When("풋살화 1건을 일괄 등록 요청한다")
    public void 풋살화_일괄_등록() {
        Map<String, Object> futsal = Map.of(
                "category", "BOOTS",
                "brand", "Nike",
                "modelCode", "FUTSAL-" + System.currentTimeMillis(),
                "bootsSpec", Map.of(
                        "studType", "TF",
                        "siloName", "Premier",
                        "releaseYear", "2025",
                        "surfaceType", "인조잔디"
                )
        );
        Map<String, Object> body = Map.of("items", List.of(futsal));
        sendBulkImport(body);
    }

    @When("빈 배열을 일괄 등록 요청한다")
    public void 빈_배열_일괄_등록() {
        Map<String, Object> body = Map.of("items", List.of());
        sendBulkImport(body);
    }

    // ===== Then =====

    @And("응답의 data의 errors 첫 항목의 {string} 필드는 {string}이다")
    public void errors_첫_항목_필드_확인(String fieldName, String expectedValue) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) context.getLastResponse().body().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) data.get("errors");

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0).get(fieldName)).asString().isEqualTo(expectedValue);
    }

    // ===== Helper =====

    private void sendBulkImport(Map<String, Object> body) {
        // /api/admin/** 는 ADMIN 권한이 필요하므로 ADR-014 도입 후 admin 토큰을 사용한다.
        // adminAccessToken 이 사전 등록되지 않았다면 기존 일반 사용자 토큰으로 폴백 (호환성용 — 시나리오는 가드 검증 시 명시적으로 admin 사전 조건을 둔다).
        String token = context.get("adminAccessToken");
        if (token == null) {
            token = context.get("accessToken");
        }
        apiClient.authenticate(token);
        TestResponse<Map<String, Object>> response = apiClient.post(BULK_IMPORT_PATH, body);
        context.setLastResponse(response);
        apiClient.clearAuth();
    }

    private static Map<String, Object> buildBootsItem(String brand, String modelCode) {
        return Map.of(
                "category", "BOOTS",
                "brand", brand,
                "modelCode", modelCode,
                "bootsSpec", Map.of(
                        "studType", "FG",
                        "siloName", "Mercurial",
                        "releaseYear", "2025",
                        "surfaceType", "천연잔디"
                )
        );
    }

    private static Map<String, Object> buildUniformItem(String clubName, String season) {
        return Map.of(
                "category", "UNIFORM",
                "brand", "Nike",
                "uniformSpec", Map.of(
                        "clubName", clubName,
                        "season", season,
                        "league", "EPL",
                        "kitType", "HOME"
                )
        );
    }
}
