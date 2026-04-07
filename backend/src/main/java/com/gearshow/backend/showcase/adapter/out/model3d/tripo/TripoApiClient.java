package com.gearshow.backend.showcase.adapter.out.model3d.tripo;

import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskRequest;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskStatusResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoUploadResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tripo REST API HTTP 클라이언트.
 * 이미지 업로드, Task 생성, Task 상태 폴링을 담당한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TripoApiClient {

    private final RestClient tripoRestClient;

    /**
     * 이미지를 Tripo에 업로드하고 image_token을 반환한다.
     *
     * @param imageBytes 이미지 바이트 데이터
     * @param filename   파일명
     * @return image_token
     */
    public String uploadImage(byte[] imageBytes, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.IMAGE_JPEG);

        TripoUploadResponse response = tripoRestClient.post()
                .uri("/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(TripoUploadResponse.class);

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new TripoApiException("Tripo 이미지 업로드 실패: " + filename);
        }

        log.info("Tripo 이미지 업로드 성공 - filename: {}, token: {}", filename, response.data().image_token());
        return response.data().image_token();
    }

    /**
     * Multiview 3D 모델 생성 Task를 요청하고 task_id를 반환한다.
     *
     * @param request Task 생성 요청
     * @return task_id
     */
    public String createTask(TripoTaskRequest request) {
        TripoTaskResponse response = tripoRestClient.post()
                .uri("/task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TripoTaskResponse.class);

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new TripoApiException("Tripo Task 생성 실패");
        }

        log.info("Tripo Task 생성 성공 - taskId: {}", response.data().task_id());
        return response.data().task_id();
    }

    /**
     * Task 상태를 조회한다.
     *
     * @param taskId Tripo Task ID
     * @return Task 상태 응답
     */
    public TripoTaskStatusResponse getTaskStatus(String taskId) {
        // 원본 JSON 로깅 (디버깅용)
        String rawJson = tripoRestClient.get()
                .uri("/task/{taskId}", taskId)
                .retrieve()
                .body(String.class);
        log.info("Tripo Task 원본 응답 - taskId: {}, json: {}", taskId, rawJson);

        TripoTaskStatusResponse response;
        try {
            response = new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(rawJson, TripoTaskStatusResponse.class);
        } catch (Exception e) {
            throw new TripoApiException("Tripo Task 응답 JSON 파싱 실패 - taskId: " + taskId + ", json: " + rawJson, e);
        }

        if (response == null || response.data() == null) {
            throw new TripoApiException("Tripo Task 상태 조회 실패 - taskId: " + taskId);
        }

        // 파싱된 결과 상세 로깅
        var data = response.data();
        log.info("Tripo Task 파싱 결과 - taskId: {}, status: {}, progress: {}, output: {}, model: {}, rendered_image: {}",
                data.task_id(), data.status(), data.progress(),
                data.output() != null ? "존재" : "null",
                data.output() != null ? data.output().model() : "N/A",
                data.output() != null ? data.output().rendered_image() : "N/A");

        return response;
    }
}
