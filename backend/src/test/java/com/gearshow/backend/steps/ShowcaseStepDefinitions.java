package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import com.gearshow.backend.support.TestResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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

    @When("축구화 스펙과 함께 쇼케이스를 등록한다")
    public void 축구화_스펙과_함께_쇼케이스_등록() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("category", "BOOTS");
        body.put("brand", "Nike");
        body.put("modelCode", "DJ2839");
        body.put("title", "머큐리얼 스펙 테스트");
        body.put("conditionGrade", "A");
        body.put("userSize", "270mm");
        // 축구화 스펙 필드
        body.put("studType", "FG");
        body.put("siloName", "Mercurial");
        body.put("releaseYear", "2025");
        body.put("surfaceType", "천연잔디");
        body.put("imageKeys", List.of("showcases/images/spec-test.jpg"));

        TestResponse<Map<String, Object>> response = apiClient.post("/api/v1/showcases", body);
        context.setLastResponse(response);
        apiClient.clearAuth();

        if (response.statusCode() == 201) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            context.put("showcaseId", ((Number) data.get("showcaseId")).longValue());
        }
    }

    @When("카탈로그 없이 직접 입력으로 쇼케이스를 등록한다")
    public void 카탈로그_없이_직접_입력_등록() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("category", "BOOTS");
        body.put("brand", "Adidas");
        body.put("title", "직접 입력 테스트");
        body.put("conditionGrade", "B");
        body.put("userSize", "260mm");
        body.put("imageKeys", List.of("showcases/images/manual-test.jpg"));

        TestResponse<Map<String, Object>> response = apiClient.post("/api/v1/showcases", body);
        context.setLastResponse(response);
        apiClient.clearAuth();

        if (response.statusCode() == 201) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            context.put("showcaseId", ((Number) data.get("showcaseId")).longValue());
        }
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
        List<String> imageKeys = IntStream.range(0, count)
                .mapToObj(i -> "showcases/images/added-" + i + ".jpg")
                .toList();

        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/" + showcaseId + "/images",
                Map.of("imageKeys", imageKeys)));
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
        List<String> modelSourceImageKeys = IntStream.range(0, 4)
                .mapToObj(i -> "showcases/model-source/source-" + i + ".jpg")
                .toList();

        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/" + showcaseId + "/3d-model",
                Map.of("modelSourceImageKeys", modelSourceImageKeys)));
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

        Map<String, Object> body = new HashMap<>();
        body.put("category", "BOOTS");
        body.put("brand", "Nike");
        body.put("title", "테스트");
        body.put("conditionGrade", "A");
        body.put("imageKeys", List.of("showcases/images/test.jpg"));

        context.setLastResponse(apiClient.post("/api/v1/showcases", body));
    }

    // ===== Presigned URL =====

    @When("SHOWCASE_IMAGE 유형으로 Presigned URL {int}개를 요청한다")
    public void showcase_image_presigned_url_요청(int count) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        List<Map<String, Object>> files = IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of(
                        "contentType", "image/jpeg",
                        "filename", "photo-" + i + ".jpg",
                        "type", "SHOWCASE_IMAGE"))
                .toList();

        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/upload-urls",
                Map.of("files", files)));
        apiClient.clearAuth();
    }

    @When("MODEL_SOURCE 유형으로 Presigned URL {int}개를 요청한다")
    public void model_source_presigned_url_요청(int count) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        List<Map<String, Object>> files = IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of(
                        "contentType", "image/jpeg",
                        "filename", "source-" + i + ".jpg",
                        "type", "MODEL_SOURCE"))
                .toList();

        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/upload-urls",
                Map.of("files", files)));
        apiClient.clearAuth();
    }

    @When("등록된 쇼케이스의 이미지 추가용 Presigned URL {int}개를 요청한다")
    public void showcase_image_추가용_presigned_url_요청(int count) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Long showcaseId = context.get("showcaseId");
        List<Map<String, Object>> files = IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of(
                        "contentType", "image/jpeg",
                        "filename", "added-" + i + ".jpg",
                        "type", "SHOWCASE_IMAGE"))
                .toList();

        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/" + showcaseId + "/images/upload-urls",
                Map.of("files", files)));
        apiClient.clearAuth();
    }

    @When("빈 파일 목록으로 Presigned URL을 요청한다")
    public void 빈_파일_목록으로_presigned_url_요청() {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        context.setLastResponse(apiClient.post(
                "/api/v1/showcases/upload-urls",
                Map.of("files", List.of())));
        apiClient.clearAuth();
    }

    @Then("응답의 data에 Presigned URL 목록이 {int}개 반환된다")
    public void presigned_url_목록_개수_검증(int expectedCount) {
        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) context.getLastResponse().body().get("data");
        assertThat(data).hasSize(expectedCount);
    }

    @Then("반환된 각 항목에 {string} 필드가 존재한다")
    public void presigned_url_항목_필드_존재_검증(String fieldName) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data =
                (List<Map<String, Object>>) context.getLastResponse().body().get("data");
        assertThat(data).allSatisfy(item ->
                assertThat(item).containsKey(fieldName));
    }

    @Then("반환된 s3Key 는 {string} 경로를 포함한다")
    public void presigned_url_s3key_경로_검증(String expectedPath) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data =
                (List<Map<String, Object>>) context.getLastResponse().body().get("data");
        assertThat(data).allSatisfy(item -> {
            String s3Key = (String) item.get("s3Key");
            assertThat(s3Key).contains(expectedPath);
        });
    }

    // ===== Helper =====

    /**
     * 쇼케이스 등록 공통 로직.
     * 클라이언트가 S3에 미리 업로드한 것으로 가정하고 S3 키 목록을 전달한다.
     *
     * @param imageCount             일반 이미지 개수
     * @param modelSourceImageCount  3D 모델 소스 이미지 개수 (0이면 미포함)
     */
    private void 등록_수행(int imageCount, int modelSourceImageCount) {
        String accessToken = context.get("accessToken");
        apiClient.authenticate(accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("category", "BOOTS");
        body.put("brand", "Nike");
        body.put("modelCode", "DJ2839");
        body.put("title", "머큐리얼 슈퍼플라이 착용 후기");
        body.put("description", "FG 천연잔디에서 5번 착용했습니다");
        body.put("userSize", "270");
        body.put("conditionGrade", "A");
        body.put("wearCount", 5);
        body.put("isForSale", true);

        // 일반 이미지 키 목록
        List<String> imageKeys = IntStream.range(0, imageCount)
                .mapToObj(i -> "showcases/images/test-image-" + i + ".jpg")
                .toList();
        body.put("imageKeys", imageKeys);

        // 3D 모델 소스 이미지 키 목록 (0이면 미포함)
        if (modelSourceImageCount > 0) {
            List<String> modelSourceImageKeys = IntStream.range(0, modelSourceImageCount)
                    .mapToObj(i -> "showcases/model-source/model-source-" + i + ".jpg")
                    .toList();
            body.put("modelSourceImageKeys", modelSourceImageKeys);
        }

        TestResponse<Map<String, Object>> response = apiClient.post("/api/v1/showcases", body);
        context.setLastResponse(response);
        apiClient.clearAuth();

        // 등록 성공 시 showcaseId 저장
        if (response.statusCode() == 201) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.body().get("data");
            context.put("showcaseId", ((Number) data.get("showcaseId")).longValue());
        }
    }
}
