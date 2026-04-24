package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3D 모델 생성 워크플로우 Worker 서비스.
 *
 * <p><b>흐름 (설계 §5, §6, §7)</b>:</p>
 * <ol>
 *   <li>TX1: 분산 락 안에서 {@code REQUESTED → PREPARING} 조건부 전이</li>
 *   <li>(락 밖) 소스 이미지 4장 S3 존재 검증 (HEAD)</li>
 *   <li>(락 밖) Tripo 이미지 업로드 + {@code POST /task} — 과금 지점</li>
 *   <li>(락 밖) {@code tripo_pending_task} 선저장 — 워커 크래시 시 task_id 유실 방지</li>
 *   <li>TX2: 분산 락 안에서 {@code PREPARING → GENERATING} + {@code tripo_task_id} 저장 + pending 삭제</li>
 * </ol>
 *
 * <p><b>락 범위 (설계 §6.1, §6.2)</b>: 상태 전이 TX 만 락 안, 외부 I/O (S3, Tripo) 는 락 밖.
 * 락은 TX 지속시간(ms 수준) 만 보유한다.</p>
 *
 * <p><b>설계 결정 #5 (예외 전파 규칙)</b>:</p>
 * <ul>
 *   <li>Tripo 호출 <b>전</b> 실패: 예외 throw → Worker 가 release() 후 Kafka 재시도 (무과금)</li>
 *   <li>Tripo 호출 <b>후</b> 실패 (TX2 DB 오류 등): 예외 삼키고 return → release 하지 않음
 *       → pending_task 레코드가 남아 Reconcile(P1-G) 이 Tripo cancel 로 정리</li>
 * </ul>
 *
 * <p><b>@RetryableTopic 과의 연계 (P1-D-δ)</b>: Retryable / CircuitBreaker 예외를 throw 하면
 * Spring Kafka 가 메시지를 retry 토픽으로 이동해 exp backoff 로 재시도한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareWorkflowService implements PrepareWorkflowUseCase {

    /** 3D 모델 생성에 필요한 최소 소스 이미지 수 (앞/뒤/좌/우). */
    private static final int MIN_SOURCE_IMAGES = 4;
    private static final String FAILURE_SOURCE_INTERNAL = "INTERNAL";
    private static final String FAILURE_SOURCE_S3 = "S3";
    private static final String FAILURE_SOURCE_TRIPO = "TRIPO_API";

    private final ModelGenerationWorkflowPort workflowPort;
    private final ModelSourceImagePort modelSourceImagePort;
    private final ImageStoragePort imageStoragePort;
    private final WorkflowLockPort workflowLockPort;
    private final ModelGenerationClient modelGenerationClient;
    private final TripoPendingTaskPort tripoPendingTaskPort;

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

        if (!validateSourceImages(workflowId, showcaseId)) {
            return;
        }

        String tripoTaskId = callTripoOrHandle(workflowId, showcaseId);
        if (tripoTaskId == null) {
            // 이미 내부 분기에서 FAILED 처리했거나 재시도용 예외를 재전파했다.
            return;
        }

        if (!preservePendingTask(workflowId, tripoTaskId)) {
            return;
        }

        transitionToGeneratingUnderLock(workflowId, tripoTaskId);
    }

    /**
     * TX1 (REQUESTED → PREPARING) 을 분산 락 안에서 수행한다. 락 busy 또는 조건부 UPDATE
     * affected=0 은 모두 "다른 Worker/Reconcile 이 처리 중" 이라는 신호로 동일하게 처리한다.
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
     * 소스 이미지 개수와 S3 객체 존재를 검증한다. 실패 시 FAILED 마킹 후 {@code false}.
     */
    private boolean validateSourceImages(Long workflowId, Long showcaseId) {
        List<String> imageUrls = modelSourceImagePort.findImageUrlsByShowcaseId(showcaseId);
        if (imageUrls.size() < MIN_SOURCE_IMAGES) {
            log.error("소스 이미지 {} 개 미달 — FAILED 전이 - workflowId: {}, showcaseId: {}, found: {}",
                    MIN_SOURCE_IMAGES, workflowId, showcaseId, imageUrls.size());
            markFailedOrLog(workflowId,
                    WorkflowFailureCode.SOURCE_IMAGES_MISSING,
                    "소스 이미지 " + MIN_SOURCE_IMAGES + "장 미달 (found=" + imageUrls.size() + ")",
                    FAILURE_SOURCE_INTERNAL);
            return false;
        }
        for (String imageUrl : imageUrls) {
            if (!imageStoragePort.existsByUrl(imageUrl)) {
                log.error("S3 객체 누락 — FAILED 전이 - workflowId: {}, imageUrl: {}",
                        workflowId, imageUrl);
                markFailedOrLog(workflowId,
                        WorkflowFailureCode.S3_KEY_MISSING,
                        "S3 객체 누락: " + imageUrl,
                        FAILURE_SOURCE_S3);
                return false;
            }
        }
        return true;
    }

    /**
     * Tripo 이미지 업로드 + {@code POST /task} 를 호출하고 task_id 를 반환한다.
     *
     * <p>에러 분류 (설계 결정 #4 / 설계 §5):</p>
     * <ul>
     *   <li>{@link ModelGenerationNonRetryableException}: 즉시 FAILED + alertRequired 시 ALERT 로그.
     *       호출자 return 유도 (null 반환).</li>
     *   <li>Retryable 계열 및 {@code CallNotPermittedException}: 그대로 throw →
     *       {@code @RetryableTopic} 이 backoff 재시도.</li>
     * </ul>
     */
    private String callTripoOrHandle(Long workflowId, Long showcaseId) {
        try {
            return modelGenerationClient.startGeneration(workflowId, showcaseId);
        } catch (ModelGenerationNonRetryableException e) {
            log.error("Tripo Non-Retryable 실패 — FAILED 전이 - workflowId: {}, errorCode: {}",
                    workflowId, e.getMessage(), e);
            markFailedOrLog(workflowId,
                    WorkflowFailureCode.TRIPO_NON_RETRYABLE,
                    e.getMessage(),
                    FAILURE_SOURCE_TRIPO);
            if (e.isAlertRequired()) {
                log.error("ALERT: Tripo 전역 장애 (크레딧/인증) 가능 — workflowId: {}, errorCode: {}",
                        workflowId, e.getMessage());
            }
            return null;
        }
    }

    /**
     * Tripo POST 성공 직후 {@code tripo_pending_task} 레코드를 선저장한다. 실패 시
     * task_id 유실 위험이 있으므로 CRITICAL 로그 + 예외 재전파로 Kafka 재시도를 유도한다.
     * 재시도 시 content_hash dedup 또는 Reconcile 이 중복 task 를 정리한다 (ADR-011 ④).
     *
     * @return 계속 진행 가능하면 {@code true}
     */
    private boolean preservePendingTask(Long workflowId, String tripoTaskId) {
        try {
            tripoPendingTaskPort.preserve(workflowId, tripoTaskId);
            return true;
        } catch (DataAccessException e) {
            log.error("CRITICAL: tripo_pending_task 선저장 실패 — taskId={} 유실 위험, 재시도 예정",
                    tripoTaskId, e);
            throw e;
        }
    }

    /**
     * TX2 를 분산 락 안에서 수행한다. affected=0 은 이미 다른 경로가 전이했다는 신호이므로
     * pending 삭제 없이 종료 (Reconcile 이 정리). {@link DataAccessException} 발생 시엔
     * pending 레코드를 그대로 두어 Reconcile 이 복구하도록 하고 예외를 삼킨다
     * (설계 결정 #5 — Tripo 호출 후 실패는 Worker release 금지).
     */
    private void transitionToGeneratingUnderLock(Long workflowId, String tripoTaskId) {
        try {
            workflowLockPort.withLock(workflowId, () -> {
                int affected = workflowPort.markGenerating(workflowId, tripoTaskId);
                if (affected == 0) {
                    log.warn("TX2 affected=0 — 다른 Worker/Reconcile 이 이미 전이한 것으로 추정. "
                                    + "pending 유지 (Reconcile 이 정리). workflowId: {}, taskId: {}",
                            workflowId, tripoTaskId);
                    return;
                }
                // TX2 성공 — pending 정리. DELETE 실패는 로그만.
                try {
                    tripoPendingTaskPort.deleteByWorkflowId(workflowId);
                } catch (DataAccessException deleteEx) {
                    log.warn("tripo_pending_task DELETE 실패 — 정리 유실. 영향 없음 "
                                    + "(pending 행은 다음 Reconcile 에서 정리됨). workflowId: {}",
                            workflowId, deleteEx);
                }
                log.info("TX2 완료 — workflowId: {}, taskId: {}", workflowId, tripoTaskId);
            });
        } catch (WorkflowLockBusyException e) {
            log.warn("TX2 락 busy — Tripo task 는 발행됐으나 상태 전이 실패. Reconcile 이 복구 예정. "
                    + "workflowId: {}, taskId: {}", workflowId, tripoTaskId);
            // Tripo POST 이미 성공 → Worker release 하면 안 됨. 예외 삼킴.
        } catch (DataAccessException e) {
            log.error("CRITICAL: TX2 DB 실패 — pending 유지, Reconcile 이 복구 예정. "
                    + "workflowId: {}, taskId: {}", workflowId, tripoTaskId, e);
            // 설계 결정 #5 — Tripo 호출 후 실패는 Worker release 금지. 예외 삼킴.
        }
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
