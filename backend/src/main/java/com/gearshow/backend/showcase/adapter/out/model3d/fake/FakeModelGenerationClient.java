package com.gearshow.backend.showcase.adapter.out.model3d.fake;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

/**
 * 가짜 3D 모델 생성 클라이언트.
 *
 * <p>Tripo 연동 전까지 사용하는 Fake 구현체. 폴링 분리 아키텍처에 맞춰
 * startGeneration / fetchStatus / fetchResult 로 분리 구현한다.</p>
 *
 * <ul>
 *   <li>startGeneration: 즉시 UUID 기반 fake task_id 반환</li>
 *   <li>fetchStatus: 80% 확률 SUCCESS, 20% 확률 FAILED 반환</li>
 *   <li>fetchResult: 쇼케이스 ID 기반 가짜 URL 반환</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "false", matchIfMissing = true)
public class FakeModelGenerationClient implements ModelGenerationClient {

    private static final double SUCCESS_RATE = 0.8;
    private static final String FAILURE_REASON_SAMPLE = "이미지 품질이 부족합니다 (Fake 시뮬레이션)";

    private final Random random = new Random();

    @Override
    public String startGeneration(Long showcase3dModelId, Long showcaseId) {
        String fakeTaskId = "fake-" + UUID.randomUUID();
        log.info("Fake startGeneration - showcase3dModelId: {}, showcaseId: {}, taskId: {}",
                showcase3dModelId, showcaseId, fakeTaskId);
        return fakeTaskId;
    }

    @Override
    public GenerationStatus fetchStatus(String taskId) {
        // 실제로는 task_id 별로 상태가 일정해야 하지만,
        // Fake 구현은 폴링 스케줄러의 1회 호출에서 즉시 결과가 나오도록 한다.
        if (random.nextDouble() < SUCCESS_RATE) {
            log.debug("Fake fetchStatus SUCCESS - taskId: {}", taskId);
            return GenerationStatus.success();
        }
        log.debug("Fake fetchStatus FAILED - taskId: {}", taskId);
        return GenerationStatus.failed(FAILURE_REASON_SAMPLE);
    }

    @Override
    public GenerationResult fetchResult(String taskId, Long showcaseId) {
        String modelFileUrl = String.format(
                "https://cdn.gearshow.com/models/%d/model.glb", showcaseId);
        String previewImageUrl = String.format(
                "https://cdn.gearshow.com/models/%d/preview.jpg", showcaseId);
        log.info("Fake fetchResult - taskId: {}, showcaseId: {}", taskId, showcaseId);
        return new GenerationResult(modelFileUrl, previewImageUrl);
    }
}
