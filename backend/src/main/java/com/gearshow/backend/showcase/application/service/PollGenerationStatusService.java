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
import org.springframework.transaction.annotation.Transactional;

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
     * 한 모델의 폴링 사이클을 수행한다. 상태별로 전용 handler 메서드에 위임하여
     * 각 분기가 단일 책임만 갖도록 한다.
     *
     * @return 이번 호출에서 COMPLETED 또는 FAILED 로 종료 상태에 도달했으면 {@code true}
     */
    private boolean pollSingle(Showcase3dModel model) {
        GenerationStatus status = modelGenerationClient.fetchStatus(model.getGenerationTaskId());

        if (status.isRunning()) {
            return handleRunning(model);
        }
        if (status.isFailed()) {
            handleFailed(model, status.failureReason());
            return true;
        }
        handleSuccess(model);
        return true;
    }

    /**
     * 아직 진행 중인 task: 타임아웃 검사 후 markPolled 또는 fail 로 전환.
     */
    @Transactional
    protected boolean handleRunning(Showcase3dModel model) {
        if (isTimedOut(model)) {
            log.warn("Tripo 폴링 타임아웃 - showcase3dModelId: {}, taskId: {}",
                    model.getId(), model.getGenerationTaskId());
            failAndSync(model, "3D 생성 시간 초과");
            return true;
        }
        showcase3dModelPort.save(model.markPolled());
        return false;
    }

    /**
     * Tripo 가 실패로 응답한 경우: failureReason 저장 + showcase 동기화.
     */
    @Transactional
    protected void handleFailed(Showcase3dModel model, String reason) {
        log.warn("Tripo 생성 실패 - showcase3dModelId: {}, taskId: {}, reason: {}",
                model.getId(), model.getGenerationTaskId(), reason);
        failAndSync(model, reason);
    }

    /**
     * Tripo 가 성공으로 응답한 경우: 결과 다운로드 + S3 저장 + COMPLETED 전환.
     *
     * <p>fetchResult 는 외부 I/O 를 동반하므로 트랜잭션 밖에서 먼저 수행하고,
     * 그 결과를 받아 DB 업데이트만 {@code completeAndSync} 에서 트랜잭션으로 묶는다.</p>
     */
    private void handleSuccess(Showcase3dModel model) {
        GenerationResult result = modelGenerationClient.fetchResult(
                model.getGenerationTaskId(), model.getShowcaseId());
        completeAndSync(model, result);
        log.info("3D 모델 생성 완료 - showcase3dModelId: {}, taskId: {}",
                model.getId(), model.getGenerationTaskId());
    }

    /**
     * 모델 COMPLETED 전환 + showcase.has3dModel 동기화를 단일 트랜잭션으로 묶는다.
     * 한쪽만 커밋되는 불일치 상태를 방지한다.
     */
    @Transactional
    protected void completeAndSync(Showcase3dModel model, GenerationResult result) {
        Showcase3dModel completed = model.complete(result.modelFileUrl(), result.previewImageUrl());
        showcase3dModelPort.save(completed);
        showcasePort.updateHas3dModel(model.getShowcaseId(), true);
    }

    /**
     * 모델 FAILED 전환 + showcase.has3dModel=false 동기화를 단일 트랜잭션으로 묶는다.
     */
    @Transactional
    protected void failAndSync(Showcase3dModel model, String reason) {
        showcase3dModelPort.save(model.fail(reason));
        showcasePort.updateHas3dModel(model.getShowcaseId(), false);
    }

    /**
     * 폴링 시작 기준 시각({@code requestedAt}) 으로부터 timeoutMinutes 이상 경과했으면 true.
     *
     * <p>엄밀히는 {@code markGenerating} 시점이 더 정확하지만 현재 도메인은 그 시각을 별도로 저장하지 않는다.
     * {@code requestedAt} 과 {@code markGenerating} 사이는 최대 수 초 이내이므로 근사로 충분하다.</p>
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
