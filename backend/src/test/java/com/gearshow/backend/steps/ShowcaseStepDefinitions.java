package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

/**
 * 쇼케이스 관련 Cucumber Step Definitions.
 * Given: 사전 조건 (쇼케이스가 이미 존재하는 상태)
 * When: 테스트 대상 행위
 * Then: AuthStepDefinitions의 공통 Then 재사용
 */
public class ShowcaseStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public ShowcaseStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    // ===== Given (사전 조건) =====

    @Given("이미지 {int}개로 쇼케이스가 등록되어 있다")
    public void 쇼케이스가_등록되어_있다(int imageCount) {
        등록_수행(imageCount, 0);
    }

    // ===== When (테스트 행위) =====

    @When("일반 이미지만으로 쇼케이스를 등록한다")
    public void 일반_이미지만으로_쇼케이스_등록() {
        등록_수행(1, 0);
    }

    @When("3D 모델 소스 이미지 {int}장과 함께 쇼케이스를 등록한다")
    public void 모델소스_이미지와_함께_쇼케이스_등록(int modelSourceImageCount) {
        등록_수행(1, modelSourceImageCount);
    }

    @When("등록된 쇼케이스 상세를 조회한다")
    public void 쇼케이스_상세_조회() {
        Long showcaseId = context.get("showcaseId");
        context.setLastResponse(apiClient.get("/api/v1/showcases/" + showcaseId));
    }

    @When("등록된 쇼케이스의 제목을 {string}으로 수정한다")
    public void 쇼케이스_수정(String newTitle) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        context.setLastResponse(apiClient.patch(
                "/api/v1/showcases/" + showcaseId,
                Map.of("title", newTitle)));
        apiClient.clearAuth();
    }

    @When("등록된 쇼케이스를 삭제한다")
    public void 쇼케이스_삭제() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        context.setLastResponse(apiClient.delete("/api/v1/showcases/" + showcaseId));
        apiClient.clearAuth();
    }

    @When("쇼케이스 목록을 조회한다")
    public void 쇼케이스_목록_조회() {
        context.setLastResponse(apiClient.get("/api/v1/showcases"));
    }

    @When("내 쇼케이스 목록을 조회한다")
    public void 내_쇼케이스_목록_조회() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);
        context.setLastResponse(apiClient.get("/api/v1/users/me/showcases"));
        apiClient.clearAuth();
    }

    @When("존재하지 않는 쇼케이스 ID {int}로 조회한다")
    public void 존재하지_않는_쇼케이스_조회(int id) {
        context.setLastResponse(apiClient.get("/api/v1/showcases/" + id));
    }

    @When("등록된 쇼케이스에 이미지 {int}개를 추가한다")
    public void 이미지_추가(int count) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (int i = 0; i < count; i++) {
            parts.add("images", createFakeImage("added-" + i + ".jpg"));
        }

        context.setLastResponse(apiClient.postMultipart(
                "/api/v1/showcases/" + showcaseId + "/images", parts));
        apiClient.clearAuth();
    }

    @When("등록된 쇼케이스의 이미지 정렬 순서를 변경한다")
    public void 이미지_정렬_변경() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");

        // 먼저 상세 조회로 이미지 ID 획득
        TestResponse<Map<String, Object>> detail = apiClient.get(
                "/api/v1/showcases/" + showcaseId);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) detail.body().get("data");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> images = (java.util.List<Map<String, Object>>) data.get("images");

        // 기존 이미지 순서 유지하며 정렬 요청
        java.util.List<Map<String, Object>> imageOrders = images.stream()
                .map(img -> Map.<String, Object>of(
                        "showcaseImageId", ((Number) img.get("showcaseImageId")).longValue(),
                        "sortOrder", ((Number) img.get("sortOrder")).intValue(),
                        "isPrimary", Boolean.TRUE.equals(img.get("isPrimary"))))
                .toList();

        context.setLastResponse(apiClient.patch(
                "/api/v1/showcases/" + showcaseId + "/images/order",
                Map.of("imageOrders", imageOrders)));
        apiClient.clearAuth();
    }

    @When("등록된 쇼케이스에 3D 모델 생성을 요청한다")
    public void 모델3d_생성_요청() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (int i = 0; i < 4; i++) {
            parts.add("modelSourceImages", createFakeImage("source-" + i + ".jpg"));
        }

        context.setLastResponse(apiClient.postMultipart(
                "/api/v1/showcases/" + showcaseId + "/3d-model", parts));
        apiClient.clearAuth();
    }

    @When("등록된 쇼케이스의 3D 모델 상태를 조회한다")
    public void 모델3d_상태_조회() {
        Long showcaseId = context.get("showcaseId");
        context.setLastResponse(apiClient.get(
                "/api/v1/showcases/" + showcaseId + "/3d-model"));
    }

    @When("인증 없이 쇼케이스를 등록한다")
    public void 인증_없이_쇼케이스_등록() {
        apiClient.clearAuth();

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("category", "BOOTS");
        parts.add("brand", "Nike");
        parts.add("title", "테스트");
        parts.add("conditionGrade", "A");
        parts.add("images", createFakeImage("test.jpg"));

        context.setLastResponse(apiClient.postMultipart("/api/v1/showcases", parts));
    }

    // ===== Helper =====

    /**
     * 쇼케이스 등록 공통 로직.
     *
     * @param imageCount             일반 이미지 개수
     * @param modelSourceImageCount  3D 모델 소스 이미지 개수 (0이면 미포함)
     */
    private void 등록_수행(int imageCount, int modelSourceImageCount) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("category", "BOOTS");
        parts.add("brand", "Nike");
        parts.add("modelCode", "DJ2839");
        parts.add("title", "머큐리얼 슈퍼플라이 착용 후기");
        parts.add("description", "FG 천연잔디에서 5번 착용했습니다");
        parts.add("userSize", "270");
        parts.add("conditionGrade", "A");
        parts.add("wearCount", "5");
        parts.add("isForSale", "true");

        // 일반 이미지
        for (int i = 0; i < imageCount; i++) {
            parts.add("images", createFakeImage("test-image-" + i + ".jpg"));
        }

        // 3D 모델 소스 이미지 (0이면 미포함)
        for (int i = 0; i < modelSourceImageCount; i++) {
            parts.add("modelSourceImages", createFakeImage("model-source-" + i + ".jpg"));
        }

        TestResponse<Map<String, Object>> response = apiClient.postMultipart(
                "/api/v1/showcases", parts);
        context.setLastResponse(response);
        apiClient.clearAuth();

        // 등록 성공 시 showcaseId 저장
        if (response.statusCode() == 201) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            context.put("showcaseId", ((Number) data.get("showcaseId")).longValue());
        }
    }

    private ByteArrayResource createFakeImage(String filename) {
        return new ByteArrayResource("fake-image-data".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
