package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskRequest.MultiviewOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripoTaskRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("multiview 팩토리 메서드는 올바른 요청을 생성한다 (옵션 없음)")
    void multiview_createsCorrectRequest() {
        // Given
        String modelVersion = "v3.0-20250812";
        List<String> imageTokens = List.of("token1", "token2", "token3", "token4");

        // When
        TripoTaskRequest request = TripoTaskRequest.multiview(
                modelVersion, imageTokens, MultiviewOptions.defaults());

        // Then
        assertThat(request.type()).isEqualTo("multiview_to_model");
        assertThat(request.model_version()).isEqualTo(modelVersion);
        assertThat(request.files()).hasSize(4);
        assertThat(request.files()).allMatch(file -> "jpg".equals(file.type()));
        assertThat(request.files().get(0).file_token()).isEqualTo("token1");
        assertThat(request.files().get(3).file_token()).isEqualTo("token4");
    }

    @Test
    @DisplayName("빈 이미지 토큰 목록으로 요청을 생성하면 빈 files가 반환된다")
    void multiview_emptyTokens_returnsEmptyFiles() {
        // Given
        List<String> emptyTokens = List.of();

        // When
        TripoTaskRequest request = TripoTaskRequest.multiview(
                "v2.5", emptyTokens, MultiviewOptions.defaults());

        // Then
        assertThat(request.files()).isEmpty();
    }

    @Test
    @DisplayName("options 가 null 이면 defaults() 가 적용되어 NPE 없이 생성된다 (방어적)")
    void multiview_nullOptions_appliesDefaults() {
        // When
        TripoTaskRequest request = TripoTaskRequest.multiview(
                "v3.0-20250812", List.of("t1"), null);

        // Then
        assertThat(request.texture_quality()).isNull();
        assertThat(request.face_limit()).isNull();
        assertThat(request.orientation()).isNull();
    }

    @Test
    @DisplayName("MultiviewOptions 명시 시 record 필드에 그대로 매핑된다")
    void multiview_withOptions_includesFields() {
        // Given
        MultiviewOptions options = new MultiviewOptions("detailed", 200_000, "align_image");

        // When
        TripoTaskRequest request = TripoTaskRequest.multiview(
                "v3.0-20250812", List.of("t1"), options);

        // Then
        assertThat(request.texture_quality()).isEqualTo("detailed");
        assertThat(request.face_limit()).isEqualTo(200_000);
        assertThat(request.orientation()).isEqualTo("align_image");
    }

    @Test
    @DisplayName("@JsonInclude(NON_NULL) — defaults() 옵션은 직렬화 시 키 자체가 제외된다")
    void multiview_defaultOptions_omitsNullFieldsFromJson() throws Exception {
        // Given
        TripoTaskRequest request = TripoTaskRequest.multiview(
                "v3.0-20250812", List.of("t1"), MultiviewOptions.defaults());

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertThat(json).contains("\"type\":\"multiview_to_model\"");
        assertThat(json).contains("\"model_version\":\"v3.0-20250812\"");
        assertThat(json).doesNotContain("texture_quality");
        assertThat(json).doesNotContain("face_limit");
        assertThat(json).doesNotContain("orientation");
    }

    @Test
    @DisplayName("@JsonInclude(NON_NULL) — 명시된 옵션은 직렬화 결과에 포함된다")
    void multiview_explicitOptions_includesFieldsInJson() throws Exception {
        // Given
        MultiviewOptions options = new MultiviewOptions("detailed", 200_000, "align_image");
        TripoTaskRequest request = TripoTaskRequest.multiview(
                "v3.0-20250812", List.of("t1"), options);

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertThat(json).contains("\"texture_quality\":\"detailed\"");
        assertThat(json).contains("\"face_limit\":200000");
        assertThat(json).contains("\"orientation\":\"align_image\"");
    }
}
