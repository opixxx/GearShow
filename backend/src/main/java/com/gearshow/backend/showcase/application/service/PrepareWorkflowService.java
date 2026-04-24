package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3D 모델 생성 워크플로우 TX1 서비스 (REQUESTED → PREPARING + S3 존재 검증).
 *
 * <p><b>락 범위 (설계 §6.1, §6.2)</b>: Worker 의 상태 전이 TX 는 Redis 분산 락
 * ({@code workflow:lock:{workflowId}}) 안에서 수행한다. 락은 TX 지속시간(ms 수준) 만 보유하며
 * 외부 I/O (S3 HEAD) 는 락 밖에서 수행한다. 실패 경로의 {@code markFailed} 는 포트의 조건부
 * UPDATE (WHERE current_step NOT IN (COMPLETED,FAILED)) 가 race 를 차단하므로 락 없이도 안전.</p>
 *
 * <p><b>트랜잭션 범위</b>: 이 서비스는 의도적으로 전체를 {@code @Transactional} 로 감싸지
 * 않는다. 포트의 조건부 UPDATE 메서드는 각각 내부에서 새 트랜잭션을 열고 즉시 커밋하며,
 * S3 HEAD 호출은 DB 커넥션을 점유하지 않고 수행되어야 한다 (HikariCP 풀 보호).</p>
 *
 * <p><b>P1-D-γ 에서 확장</b>: Tripo upload + POST /task + {@code tripo_pending_task}
 * 선저장 + {@code PREPARING → GENERATING} 전이가 이 메서드 뒤에 연결된다. TX2 도 동일하게
 * 같은 락을 재획득해 감쌀 예정이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareWorkflowService implements PrepareWorkflowUseCase {

    /** 3D 모델 생성에 필요한 최소 소스 이미지 수 (앞/뒤/좌/우). */
    private static final int MIN_SOURCE_IMAGES = 4;
    private static final String FAILURE_SOURCE_INTERNAL = "INTERNAL";
    private static final String FAILURE_SOURCE_S3 = "S3";

    private final ModelGenerationWorkflowPort workflowPort;
    private final ModelSourceImagePort modelSourceImagePort;
    private final ImageStoragePort imageStoragePort;
    private final WorkflowLockPort workflowLockPort;

    @Override
    public void prepare(Long workflowId) {
        Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(workflowId);
        if (snapshot.isEmpty()) {
            log.warn("워크플로우 조회 실패 - 무시 - workflowId: {}", workflowId);
            return;
        }
        Long showcaseId = snapshot.get().showcaseId();

        if (!transitionToPreparingUnderLock(workflowId)) {
            return;
        }

        List<String> imageUrls = modelSourceImagePort.findImageUrlsByShowcaseId(showcaseId);
        if (imageUrls.size() < MIN_SOURCE_IMAGES) {
            log.error("소스 이미지 {} 개 미달 — FAILED 전이 - workflowId: {}, showcaseId: {}, found: {}",
                    MIN_SOURCE_IMAGES, workflowId, showcaseId, imageUrls.size());
            markFailedOrLog(workflowId,
                    WorkflowFailureCode.SOURCE_IMAGES_MISSING,
                    "소스 이미지 " + MIN_SOURCE_IMAGES + "장 미달 (found=" + imageUrls.size() + ")",
                    FAILURE_SOURCE_INTERNAL);
            return;
        }

        for (String imageUrl : imageUrls) {
            if (!imageStoragePort.existsByUrl(imageUrl)) {
                log.error("S3 객체 누락 — FAILED 전이 - workflowId: {}, imageUrl: {}",
                        workflowId, imageUrl);
                markFailedOrLog(workflowId,
                        WorkflowFailureCode.S3_KEY_MISSING,
                        "S3 객체 누락: " + imageUrl,
                        FAILURE_SOURCE_S3);
                return;
            }
        }

        log.info("TX1 완료 — workflowId: {}, showcaseId: {}, 소스 이미지 {} 장 존재 확인",
                workflowId, showcaseId, imageUrls.size());
        // P1-D-γ 에서 Tripo 호출·TX2 가 이 뒤에 이어진다.
    }

    /**
     * TX1 (REQUESTED → PREPARING) 을 분산 락 안에서 수행한다. 락 busy 또는 조건부 UPDATE
     * affected=0 은 모두 "다른 Worker/Reconcile 이 처리 중" 이라는 신호로 동일하게 처리한다.
     *
     * @return 전이에 성공해 후속(S3 검증) 을 계속해야 하면 {@code true}
     */
    private boolean transitionToPreparingUnderLock(Long workflowId) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        try {
            workflowLockPort.withLock(workflowId, () -> {
                int affected = workflowPort.updateStepIfCurrent(
                        workflowId, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);
                transitioned.set(affected == 1);
            });
        } catch (WorkflowLockBusyException e) {
            log.info("workflow 락 busy — 다른 Worker/Reconcile 처리 중, skip. workflowId: {}",
                    workflowId);
            return false;
        }
        if (!transitioned.get()) {
            log.info("REQUESTED→PREPARING 전환 실패 (이미 처리 중 또는 다른 상태) - workflowId: {}",
                    workflowId);
            return false;
        }
        return true;
    }

    /**
     * {@code markFailed} 를 호출하고 affected 행이 0 이면 운영 감시용 경고를 남긴다.
     * affected=0 은 다른 Worker/Reconcile 이 이미 이 워크플로우를 종결한 경우로,
     * 정상 흐름일 수도 있지만 이중 FAILED 시도라는 race 신호이기도 하므로 관측 포인트로 둔다.
     */
    private void markFailedOrLog(Long workflowId, WorkflowFailureCode code,
                                 String message, String source) {
        int affected = workflowPort.markFailed(workflowId, code, message, source);
        if (affected == 0) {
            log.warn("markFailed 무시됨 (이미 종결된 워크플로우 덮어쓰기 방지) - "
                            + "workflowId: {}, attemptedCode: {}",
                    workflowId, code);
        }
    }
}
