package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.PollGenerationStatusUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationStatus;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.infrastructure.config.TripoPollingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tripo 생성 상태 폴링 서비스.
 *
 * <p>폴링 스케줄러가 호출하면 현재 GENERATING 상태의 모델들을 배치로 조회하여
 * 각 task 의 상태를 Tripo 에서 확인하고 다음 단계로 진행한다:</p>
 * <ul>
 *   <li><b>RUNNING</b>: 타임아웃 검사 후 계속 진행하거나 FAILED 로 전환</li>
 *   <li><b>SUCCESS</b>: {@link ModelGenerationClient#fetchResult} 로 결과 다운로드 → COMPLETED + has3dModel 동기화</li>
 *   <li><b>FAILED</b>: 실패 사유 저장 + FAILED 전환</li>
 * </ul>
 *
 * <p>개별 모델 처리 중 예외가 발생해도 다른 모델 처리에 영향이 없도록
 * 각 모델을 독립적으로 try-catch 로 감싼다. 예외가 발생한 모델은 다음 폴링 사이클에
 * 다시 시도된다 (lastPolledAt 이 업데이트되지 않았으므로 우선순위도 유지).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PollGenerationStatusService implements PollGenerationStatusUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ShowcasePort showcasePort;
    private final ModelGenerationClient modelGenerationClient;
    private final TripoPollingProperties properties;

    @Override
    public int pollOnce() {
        List<Showcase3dModel> targets = showcase3dModelPort
                .findPollableGeneratingTasks(properties.batchSize());
        if (targets.isEmpty()) {
            return 0;
        }

        int terminalCount = 0;
        for (Showcase3dModel model : targets) {
            try {
                if (pollSingle(model)) {
                    terminalCount++;
                }
            } catch (RuntimeException e) {
                // 다음 사이클에서 자동 재시도 — 개별 실패가 배치 전체를 막지 않도록 격리
                log.error("Tripo 폴링 중 예외 발생 - showcase3dModelId: {}, taskId: {}",
                        model.getId(), model.getGenerationTaskId(), e);
            }
        }
        return terminalCount;
    }

    /**
     * 한 모델의 폴링을 처리한다.
     *
     * @return 이번 호출에서 COMPLETED 또는 FAILED 로 종료 상태에 도달했으면 {@code true}
     */
    private boolean pollSingle(Showcase3dModel model) {
        String taskId = model.getGenerationTaskId();
        GenerationStatus status = modelGenerationClient.fetchStatus(taskId);

        if (status.isRunning()) {
            if (isTimedOut(model)) {
                log.warn("Tripo 폴링 타임아웃 - showcase3dModelId: {}, taskId: {}",
                        model.getId(), taskId);
                showcase3dModelPort.save(model.fail("3D 생성 시간 초과"));
                showcasePort.updateHas3dModel(model.getShowcaseId(), false);
                return true;
            }
            // 아직 진행 중 — lastPolledAt 만 업데이트
            showcase3dModelPort.save(model.markPolled());
            return false;
        }

        if (status.isFailed()) {
            log.warn("Tripo 생성 실패 - showcase3dModelId: {}, taskId: {}, reason: {}",
                    model.getId(), taskId, status.failureReason());
            showcase3dModelPort.save(model.fail(status.failureReason()));
            showcasePort.updateHas3dModel(model.getShowcaseId(), false);
            return true;
        }

        // SUCCESS — 결과 다운로드 + S3 저장
        GenerationResult result = modelGenerationClient.fetchResult(taskId, model.getShowcaseId());
        Showcase3dModel completed = model.complete(result.modelFileUrl(), result.previewImageUrl());
        showcase3dModelPort.save(completed);
        showcasePort.updateHas3dModel(model.getShowcaseId(), true);
        log.info("3D 모델 생성 완료 - showcase3dModelId: {}, taskId: {}", model.getId(), taskId);
        return true;
    }

    /**
     * 폴링 시작 기준 시각({@code requestedAt}) 으로부터 timeoutMinutes 이상 경과했으면 true.
     */
    private boolean isTimedOut(Showcase3dModel model) {
        Instant reference = model.getRequestedAt() != null
                ? model.getRequestedAt()
                : model.getCreatedAt();
        if (reference == null) {
            return false;
        }
        Duration elapsed = Duration.between(reference, Instant.now());
        return elapsed.toMinutes() >= properties.taskTimeoutMinutes();
    }
}
