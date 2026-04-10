package com.gearshow.backend.showcase.adapter.out.model3d.fake;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 가짜 3D 모델 생성 클라이언트.
 *
 * <p>Tripo 연동 전까지 사용하는 Fake 구현체. 폴링 분리 아키텍처에 맞춰
 * startGeneration / fetchStatus / fetchResult 로 분리 구현한다.</p>
 *
 * <ul>
 *   <li>startGeneration: 즉시 UUID 기반 fake task_id 반환</li>
 *   <li>fetchStatus: task_id 해시 기반 <b>결정론적</b> 80% SUCCESS / 20% FAILED</li>
 *   <li>fetchResult: 쇼케이스 ID 기반 가짜 URL 반환</li>
 * </ul>
 *
 * <p><b>결정론 보장</b>: 같은 {@code taskId} 에 대해 {@link #fetchStatus} 는 항상 같은
 * 결과를 반환해야 한다. 그렇지 않으면 폴링 스케줄러가 재시도 / 크래시 복구 시나리오에서
 * 이전 판정과 모순된 결과를 보게 되어 상태 머신이 꼬인다. 해시 기반 분기로 무작위성 제거.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tripo.enabled", havingValue = "false", matchIfMissing = true)
public class FakeModelGenerationClient implements ModelGenerationClient {

    /** 80% 는 SUCCESS, 20% 는 FAILED. taskId 해시 % 100 < 80 기준. */
    private static final int SUCCESS_THRESHOLD = 80;
    private static final String FAILURE_REASON_SAMPLE = "이미지 품질이 부족합니다 (Fake 시뮬레이션)";

    @Override
    public String startGeneration(Long showcase3dModelId, Long showcaseId) {
        String fakeTaskId = "fake-" + UUID.randomUUID();
        log.info("Fake 3D 모델 생성 시작 - showcase3dModelId: {}, showcaseId: {}, taskId: {}",
                showcase3dModelId, showcaseId, fakeTaskId);
        return fakeTaskId;
    }

    @Override
    public GenerationStatus fetchStatus(String taskId) {
        // taskId 해시 % 100 기반 결정론적 분기 — 같은 taskId 는 항상 같은 결과를 돌려준다.
        // 재시도/복구 시나리오에서 상태가 뒤집히는 일을 방지한다.
        int bucket = Math.floorMod(taskId.hashCode(), 100);
        if (bucket < SUCCESS_THRESHOLD) {
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
        log.info("Fake 3D 모델 결과 조회 - taskId: {}, showcaseId: {}", taskId, showcaseId);
        return new GenerationResult(modelFileUrl, previewImageUrl);
    }
}
