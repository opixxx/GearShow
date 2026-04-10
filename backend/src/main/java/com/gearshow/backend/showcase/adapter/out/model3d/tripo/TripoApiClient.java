package com.gearshow.backend.showcase.adapter.out.model3d.tripo;

import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskRequest;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskStatusResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoUploadResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
 *
 * <p><b>Circuit Breaker</b>: 모든 Tripo HTTP 호출은 {@code @CircuitBreaker(name = "tripo")}
 * 로 보호된다. Tripo 서비스 장애 시 Circuit 이 OPEN 으로 전환되어 모든 후속 호출이
 * 즉시 {@code CallNotPermittedException} 을 던진다 (fast-fail). 상위 계층
 * ({@code PrepareModelGenerationService}) 은 이 예외를 잡아 모델을 UNAVAILABLE 상태로 전환.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TripoApiClient {

    private static final String CIRCUIT_NAME = "tripo";

    private final RestClient tripoRestClient;

    /**
     * 이미지를 Tripo에 업로드하고 image_token을 반환한다.
     */
    @CircuitBreaker(name = CIRCUIT_NAME)
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
            throw new TripoApiException(ErrorCode.TRIPO_UPLOAD_FAILED);
        }

        String token = response.data().image_token();
        log.info("Tripo 이미지 업로드 성공 - filename: {}, token: {}...{}",
                filename, token.substring(0, 4), token.substring(token.length() - 4));
        return response.data().image_token();
    }

    /**
     * Multiview 3D 모델 생성 Task를 요청하고 task_id를 반환한다.
     */
    @CircuitBreaker(name = CIRCUIT_NAME)
    public String createTask(TripoTaskRequest request) {
        TripoTaskResponse response = tripoRestClient.post()
                .uri("/task")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TripoTaskResponse.class);

        if (response == null || response.code() != 0 || response.data() == null) {
            throw new TripoApiException(ErrorCode.TRIPO_TASK_CREATION_FAILED);
        }

        log.info("Tripo Task 생성 성공 - taskId: {}", response.data().task_id());
        return response.data().task_id();
    }

    /**
     * Task 상태를 조회한다.
     */
    @CircuitBreaker(name = CIRCUIT_NAME)
    public TripoTaskStatusResponse getTaskStatus(String taskId) {
        TripoTaskStatusResponse response = tripoRestClient.get()
                .uri("/task/{taskId}", taskId)
                .retrieve()
                .body(TripoTaskStatusResponse.class);

        if (response == null || response.data() == null) {
            throw new TripoApiException(ErrorCode.TRIPO_TASK_STATUS_FAILED);
        }

        log.debug("Tripo Task 상태 - taskId: {}, status: {}, progress: {}%",
                response.data().task_id(), response.data().status(), response.data().progress());
        return response;
    }
}
