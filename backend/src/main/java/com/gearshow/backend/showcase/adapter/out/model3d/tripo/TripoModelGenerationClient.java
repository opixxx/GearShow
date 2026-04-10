package com.gearshow.backend.showcase.adapter.out.model3d.tripo;

import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskRequest;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto.TripoTaskStatusResponse;
import com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception.TripoApiException;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import com.gearshow.backend.showcase.infrastructure.config.TripoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tripo API 를 사용한 3D 모델 생성 클라이언트.
 *
 * <p><b>폴링 분리 아키텍처</b> 적용:</p>
 * <ul>
 *   <li>{@link #startGeneration(Long, Long)} — Worker 에서 호출, Tripo task 생성까지만 수행.
 *       이전 구조에서 "Worker 스레드가 5분 블로킹" 문제를 여기서 제거한다.</li>
 *   <li>{@link #fetchStatus(String)} — 폴링 스케줄러에서 주기적으로 호출. 1회 HTTP 로 상태만 확인.</li>
 *   <li>{@link #fetchResult(String, Long)} — Tripo 가 success 로 떨어진 후, 결과 파일을
 *       S3 에 저장하는 단계. 같은 S3 key 를 사용하므로 재실행해도 멱등적이다.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TripoModelGenerationClient implements ModelGenerationClient {

    private final TripoApiClient tripoApiClient;
    private final TripoConfig tripoConfig;
    private final ModelSourceImagePort modelSourceImagePort;
    private final ImageStoragePort imageStoragePort;
    private final RestClient downloadRestClient = RestClient.create();

    @Override
    public String startGeneration(Long showcase3dModelId, Long showcaseId) {
        log.info("Tripo startGeneration - showcase3dModelId: {}, showcaseId: {}",
                showcase3dModelId, showcaseId);

        // 1. 소스 이미지 조회 (앞/뒤/좌/우 순서)
        List<ModelSourceImage> sourceImages = modelSourceImagePort
                .findByShowcase3dModelId(showcase3dModelId)
                .stream()
                .sorted(Comparator.comparingInt(ModelSourceImage::getSortOrder))
                .toList();

        // 2. S3 다운로드 → Tripo 업로드 → image_token 획득
        List<String> imageTokens = uploadImagesToTripo(sourceImages);

        // 3. Multiview Task 생성 (여기서 과금 발생 — 정확히 1회만 호출)
        TripoTaskRequest taskRequest = TripoTaskRequest.multiview(
                tripoConfig.getModelVersion(), imageTokens);
        String taskId = tripoApiClient.createTask(taskRequest);
        log.info("Tripo task 생성 성공 - showcase3dModelId: {}, taskId: {}",
                showcase3dModelId, taskId);
        return taskId;
    }

    @Override
    public GenerationStatus fetchStatus(String taskId) {
        TripoTaskStatusResponse response = tripoApiClient.getTaskStatus(taskId);

        if (!response.isTerminal()) {
            log.debug("Tripo task 진행 중 - taskId: {}, status: {}, progress: {}%",
                    taskId, response.data().status(), response.data().progress());
            return GenerationStatus.running();
        }

        if (response.isSuccess()) {
            log.info("Tripo task 완료 - taskId: {}", taskId);
            return GenerationStatus.success();
        }

        String reason = "Tripo 모델 생성 실패 - 상태: " + response.data().status();
        log.warn("Tripo task 실패 - taskId: {}, reason: {}", taskId, reason);
        return GenerationStatus.failed(reason);
    }

    @Override
    public GenerationResult fetchResult(String taskId, Long showcaseId) {
        TripoTaskStatusResponse status = tripoApiClient.getTaskStatus(taskId);

        if (status.data().output() == null) {
            throw new TripoApiException(ErrorCode.TRIPO_DOWNLOAD_FAILED);
        }

        var output = status.data().output();
        // multiview → pbr_model, image_to_model → model 에 GLB URL 이 반환된다.
        String tripoModelUrl = output.pbr_model() != null ? output.pbr_model() : output.model();
        log.info("Tripo 출력 확인 - model 존재: {}, pbr_model 존재: {}, rendered_image 존재: {}",
                output.model() != null, output.pbr_model() != null, output.rendered_image() != null);

        if (tripoModelUrl == null) {
            throw new TripoApiException(ErrorCode.TRIPO_DOWNLOAD_FAILED);
        }

        // GLB 다운로드 + S3 저장 (S3 key 고정 → 재실행 시 덮어쓰기, 멱등)
        byte[] modelBytes = downloadFromUrl(tripoModelUrl);
        String modelS3Key = "models/" + showcaseId + "/model.glb";
        String modelUrl = imageStoragePort.upload(modelS3Key, modelBytes, "model/gltf-binary");

        // 프리뷰 다운로드 + S3 저장 (있으면)
        String previewUrl = null;
        if (output.rendered_image() != null) {
            byte[] previewBytes = downloadFromUrl(output.rendered_image());
            String previewS3Key = "models/" + showcaseId + "/preview.png";
            previewUrl = imageStoragePort.upload(previewS3Key, previewBytes, "image/png");
        }

        log.info("Tripo 결과 저장 완료 - showcaseId: {}, modelUrl: {}", showcaseId, modelUrl);
        return new GenerationResult(modelUrl, previewUrl);
    }

    /**
     * 소스 이미지를 S3 에서 다운로드하여 Tripo 에 업로드한다.
     */
    private List<String> uploadImagesToTripo(List<ModelSourceImage> sourceImages) {
        List<String> tokens = new ArrayList<>();
        for (ModelSourceImage image : sourceImages) {
            byte[] imageBytes = imageStoragePort.download(image.getImageUrl());
            String filename = image.getAngleType().name().toLowerCase() + ".jpg";
            String token = tripoApiClient.uploadImage(imageBytes, filename);
            tokens.add(token);
        }
        log.info("Tripo 이미지 업로드 완료 - {}장", tokens.size());
        return tokens;
    }

    /**
     * 외부 URL 에서 바이트 데이터를 다운로드한다.
     * Tripo 의 임시 다운로드 URL (5분 만료) 에서 파일을 가져온다.
     */
    private byte[] downloadFromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new TripoApiException(ErrorCode.TRIPO_DOWNLOAD_FAILED);
        }
        String resolvedUrl = url.startsWith("http") ? url : "https://" + url;
        String safeUrl = resolvedUrl.contains("?")
                ? resolvedUrl.substring(0, resolvedUrl.indexOf("?"))
                : resolvedUrl;
        log.info("Tripo 파일 다운로드 시작 - URL: {}", safeUrl);
        try {
            byte[] body = downloadRestClient.get()
                    .uri(java.net.URI.create(resolvedUrl))
                    .retrieve()
                    .body(byte[].class);
            if (body == null) {
                throw new TripoApiException(ErrorCode.TRIPO_DOWNLOAD_FAILED);
            }
            return body;
        } catch (TripoApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tripo 파일 다운로드 실패 - URL: {}", safeUrl, e);
            throw new TripoApiException(ErrorCode.TRIPO_DOWNLOAD_FAILED);
        }
    }
}
