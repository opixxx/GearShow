package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripoTaskRequestTest {

    @Test
    @DisplayName("multiview 팩토리 메서드는 올바른 요청을 생성한다")
    void multiview_createsCorrectRequest() {
        // Given
        String modelVersion = "v2.5-20250123";
        List<String> imageTokens = List.of("token1", "token2", "token3", "token4");

        // When
        TripoTaskRequest request = TripoTaskRequest.multiview(modelVersion, imageTokens);

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
        TripoTaskRequest request = TripoTaskRequest.multiview("v2.5", emptyTokens);

        // Then
        assertThat(request.files()).isEmpty();
    }
}
