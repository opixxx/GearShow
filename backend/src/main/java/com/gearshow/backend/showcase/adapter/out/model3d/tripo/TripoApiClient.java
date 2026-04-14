package com.gearshow.backend.showcase.adapter.out.model3d.tripo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoErrorResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskRequest;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskStatusResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoUploadResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoApiException;
import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;
import com.gearshow.backend.showcase.application.exception.ModelGenerationRetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * Tripo REST API HTTP 클라이언트.
 * 이미지 업로드, Task 생성, Task 상태 폴링을 담당한다.
 *
 * <p><b>에러 분류 (설계 결정 #4)</b>: Tripo 의 HTTP 4xx/5xx 응답을 가로채어
 * 내부 에러 코드 기반으로 Retryable / Non-retryable 로 분류한다.
 * 분류된 예외는 Application 계층({@code PrepareModelGenerationService})이
 * catch 하여 각각 다른 복구 전략을 적용한다.</p>
 *
 * <p><b>Circuit Breaker</b>: 모든 Tripo HTTP 호출은 {@code @CircuitBreaker(name = "tripo")}
 * 로 보호된다. Tripo 서비스 장애 시 Circuit 이 OPEN 으로 전환되어 모든 후속 호출이
 * 즉시 {@code CallNotPermittedException} 을 던진다 (fast-fail).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TripoApiClient {

    private static final String CIRCUIT_NAME = "tripo";

    /** Tripo 에러 코드 중 재시도 가능한 것: 429 Rate Limit (1007, 2000) + 500 Server Error (1000, 1001). */
    private static final Set<Integer> RETRYABLE_CODES = Set.of(1000, 1001, 1007, 2000);

    /** Tripo 에러 코드 중 전체 서비스에 영향을 주어 Alert 가 필요한 것. */
    private static final Set<Integer> ALERT_REQUIRED_CODES = Set.of(1002, 1005, 2010);

    private final RestClient tripoRestClient;
    private final ObjectMapper objectMapper;

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
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, res) -> handleErrorResponse(res.getStatusCode().value(), res.getBody()))
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
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, res) -> handleErrorResponse(res.getStatusCode().value(), res.getBody()))
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
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, res) -> handleErrorResponse(res.getStatusCode().value(), res.getBody()))
                .body(TripoTaskStatusResponse.class);

        if (response == null || response.data() == null) {
            throw new TripoApiException(ErrorCode.TRIPO_TASK_STATUS_FAILED);
        }

        log.debug("Tripo Task 상태 - taskId: {}, status: {}, progress: {}%",
                response.data().task_id(), response.data().status(), response.data().progress());
        return response;
    }

    /**
     * Tripo HTTP 에러 응답을 파싱하여 Retryable / Non-retryable 예외로 분류한다.
     *
     * <p>분류 기준 (Tripo 공식 에러 코드):</p>
     * <ul>
     *   <li><b>Retryable</b>: 1000, 1001 (서버 에러), 1007, 2000 (Rate Limit)</li>
     *   <li><b>Non-retryable + Alert</b>: 1002 (인증), 1005 (권한), 2010 (크레딧)</li>
     *   <li><b>Non-retryable</b>: 나머지 (1003, 1004, 2003, 2004, 2008, 2009 등)</li>
     * </ul>
     */
    private void handleErrorResponse(int httpStatus, java.io.InputStream body) {
        TripoErrorResponse error = parseErrorBody(body);
        int tripoCode = error != null ? error.code() : 0;
        String tripoMessage = error != null ? error.message() : "응답 파싱 실패 (HTTP " + httpStatus + ")";

        log.warn("Tripo API 에러 - httpStatus: {}, tripoCode: {}, message: {}",
                httpStatus, tripoCode, tripoMessage);

        if (RETRYABLE_CODES.contains(tripoCode)) {
            throw new ModelGenerationRetryableException(mapToRetryableErrorCode(tripoCode));
        }

        boolean alertRequired = ALERT_REQUIRED_CODES.contains(tripoCode);
        throw new ModelGenerationNonRetryableException(
                mapToNonRetryableErrorCode(tripoCode), alertRequired);
    }

    /**
     * Tripo 에러 응답 body 를 파싱한다. 파싱 실패 시 null 반환.
     */
    private TripoErrorResponse parseErrorBody(java.io.InputStream body) {
        try {
            return objectMapper.readValue(body, TripoErrorResponse.class);
        } catch (Exception e) {
            log.warn("Tripo 에러 응답 파싱 실패 — 기본 Non-retryable 로 처리", e);
            return null;
        }
    }

    /**
     * Retryable Tripo 에러 코드를 ErrorCode 로 매핑한다.
     */
    private ErrorCode mapToRetryableErrorCode(int tripoCode) {
        return switch (tripoCode) {
            case 1007, 2000 -> ErrorCode.TRIPO_RATE_LIMITED;
            case 1000, 1001 -> ErrorCode.TRIPO_SERVER_ERROR;
            default -> ErrorCode.TRIPO_SERVER_ERROR;
        };
    }

    /**
     * Non-retryable Tripo 에러 코드를 ErrorCode 로 매핑한다.
     */
    private ErrorCode mapToNonRetryableErrorCode(int tripoCode) {
        return switch (tripoCode) {
            case 1002 -> ErrorCode.TRIPO_AUTH_FAILED;
            case 1005, 2010 -> ErrorCode.TRIPO_INSUFFICIENT_CREDIT;
            default -> ErrorCode.TRIPO_INVALID_REQUEST;
        };
    }
}
