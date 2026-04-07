package com.gearshow.backend.showcase.adapter.out.model3d.tripo;

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
 * Tripo API를 사용한 3D 모델 생성 클라이언트.
 *
 * <p>전체 흐름:
 * 1. DB에서 소스 이미지 목록 조회
 * 2. S3에서 소스 이미지 다운로드
 * 3. Tripo에 이미지 업로드 → image_token 획득
 * 4. Multiview Task 생성 → task_id 획득
 * 5. Task 상태 폴링 (3초 간격, 최대 5분)
 * 6. 성공 시 GLB/프리뷰 다운로드 → S3에 영구 저장
 * 7. GenerationResult 반환</p>
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

    @Override
    public GenerationResult generate(Long showcase3dModelId, Long showcaseId) {
        try {
            log.info("Tripo 3D 모델 생성 시작 - showcase3dModelId: {}, showcaseId: {}",
                    showcase3dModelId, showcaseId);

            // 1. 소스 이미지 조회 (앞/뒤/좌/우 순서)
            List<ModelSourceImage> sourceImages = modelSourceImagePort
                    .findByShowcase3dModelId(showcase3dModelId)
                    .stream()
                    .sorted(Comparator.comparingInt(ModelSourceImage::getSortOrder))
                    .toList();

            // 2. S3 다운로드 → Tripo 업로드 → image_token 획득
            List<String> imageTokens = uploadImagesToTripo(sourceImages);

            // 3. Multiview Task 생성
            TripoTaskRequest taskRequest = TripoTaskRequest.multiview(
                    tripoConfig.getModelVersion(), imageTokens);
            String taskId = tripoApiClient.createTask(taskRequest);

            // 4. 폴링하여 완료 대기
            TripoTaskStatusResponse status = pollUntilComplete(taskId);

            // 5. 성공/실패 처리
            if (status.isSuccess()) {
                return handleSuccess(status, showcaseId);
            }
            return GenerationResult.failure(
                    "Tripo 모델 생성 실패 - 상태: " + status.data().status());

        } catch (TripoApiException e) {
            log.error("Tripo API 호출 실패 - showcase3dModelId: {}", showcase3dModelId, e);
            return GenerationResult.failure("Tripo API 호출 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("3D 모델 생성 중 예외 발생 - showcase3dModelId: {}", showcase3dModelId, e);
            return GenerationResult.failure("3D 모델 생성 중 예외 발생: " + e.getMessage());
        }
    }

    /**
     * 소스 이미지를 S3에서 다운로드하여 Tripo에 업로드한다.
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
     * Task 상태를 폴링하여 완료(성공/실패)될 때까지 대기한다.
     */
    private TripoTaskStatusResponse pollUntilComplete(String taskId) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = tripoConfig.getTimeoutMs();
        long pollIntervalMs = tripoConfig.getPollIntervalMs();

        while (true) {
            TripoTaskStatusResponse status = tripoApiClient.getTaskStatus(taskId);

            if (status.isTerminal()) {
                log.info("Tripo Task 완료 - taskId: {}, status: {}, progress: {}%",
                        taskId, status.data().status(), status.data().progress());
                return status;
            }

            // 타임아웃 확인
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMs) {
                throw new TripoApiException(
                        "Tripo Task 타임아웃 - taskId: " + taskId + ", 경과시간: " + elapsed + "ms");
            }

            log.debug("Tripo Task 진행 중 - taskId: {}, status: {}, progress: {}%",
                    taskId, status.data().status(), status.data().progress());

            sleep(pollIntervalMs);
        }
    }

    /**
     * 생성 성공 시 GLB/프리뷰를 S3에 저장하고 URL을 반환한다.
     */
    private GenerationResult handleSuccess(TripoTaskStatusResponse status, Long showcaseId) {
        var output = status.data().output();

        // Tripo에서 GLB 모델 다운로드
        byte[] modelBytes = downloadFromUrl(output.model());
        String modelS3Key = "models/" + showcaseId + "/model.glb";
        String modelUrl = imageStoragePort.upload(modelS3Key, modelBytes, "model/gltf-binary");

        // Tripo에서 프리뷰 이미지 다운로드
        String previewUrl = null;
        if (output.rendered_image() != null) {
            byte[] previewBytes = downloadFromUrl(output.rendered_image());
            String previewS3Key = "models/" + showcaseId + "/preview.png";
            previewUrl = imageStoragePort.upload(previewS3Key, previewBytes, "image/png");
        }

        log.info("Tripo 3D 모델 S3 저장 완료 - showcaseId: {}, modelUrl: {}", showcaseId, modelUrl);
        return GenerationResult.success(modelUrl, previewUrl);
    }

    /**
     * 외부 URL에서 바이트 데이터를 다운로드한다.
     * Tripo의 임시 다운로드 URL(5분 만료)에서 파일을 가져온다.
     */
    private byte[] downloadFromUrl(String url) {
        return RestClient.create().get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TripoApiException("Tripo 폴링 중 인터럽트 발생");
        }
    }
}
