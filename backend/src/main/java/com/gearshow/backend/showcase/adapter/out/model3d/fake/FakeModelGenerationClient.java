package com.gearshow.backend.showcase.adapter.out.model3d.fake;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 가짜 3D 모델 생성 클라이언트.
 * 외부 3D 생성 API(Tripo 등)가 연동되기 전까지 사용하는 Fake 구현체.
 *
 * <p>약 2초의 지연 후 80% 확률로 성공, 20% 확률로 실패를 시뮬레이션한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "false", matchIfMissing = true)
public class FakeModelGenerationClient implements ModelGenerationClient {

    private static final int FAKE_DELAY_MS = 2000;
    private static final double SUCCESS_RATE = 0.8;
    private static final String FAKE_PROVIDER = "fake-tripo";

    private final Random random = new Random();

    @Override
    public GenerationResult generate(Long showcase3dModelId, Long showcaseId) {
        log.info("가짜 3D 모델 생성 시작 - showcase3dModelId: {}, showcaseId: {}",
                showcase3dModelId, showcaseId);

        simulateDelay();

        if (random.nextDouble() < SUCCESS_RATE) {
            String modelFileUrl = String.format(
                    "https://cdn.gearshow.com/models/%d/model.glb", showcaseId);
            String previewImageUrl = String.format(
                    "https://cdn.gearshow.com/models/%d/preview.jpg", showcaseId);

            log.info("가짜 3D 모델 생성 성공 - showcase3dModelId: {}", showcase3dModelId);
            return GenerationResult.success(modelFileUrl, previewImageUrl);
        }

        log.warn("가짜 3D 모델 생성 실패 (시뮬레이션) - showcase3dModelId: {}", showcase3dModelId);
        return GenerationResult.failure("이미지 품질이 부족합니다 (Fake 시뮬레이션)");
    }

    private void simulateDelay() {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
