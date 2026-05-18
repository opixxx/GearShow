package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.event.WorkflowGeneratingConfirmedEvent;
import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.AdmissionMetricsPort;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 *   <li>(락 밖) 소스 이미지 4장 S3 존재 검증 (HEAD) — 영구 실패성 검증을 먼저 수행해 실패 시 락 획득 비용을 절약하고
 *       PREPARING 일시 전이 없이 바로 {@code REQUESTED → FAILED} 로 종결한다.</li>
 *   <li>TX1: 분산 락 안에서 {@code REQUESTED → PREPARING} 조건부 전이</li>
 *   <li>(락 밖) Tripo 이미지 업로드 + {@code POST /task} — 과금 지점</li>
 *   <li>(락 밖) {@code tripo_pending_task} 선저장 — 워커 크래시 시 task_id 유실 방지</li>
 *   <li>TX2: 분산 락 안에서 {@code PREPARING → GENERATING} + {@code tripo_task_id} 저장 + pending 삭제</li>
 * </ol>
 *
 * <p><b>락 범위 (설계 §6.1, §6.2)</b>: 상태 전이 TX 만 락 안, 외부 I/O (S3, Tripo) 는 락 밖.
 * 락은 TX 지속시간(ms 수준) 만 보유한다.</p>
 *
 * <p><b>입장 게이트 (ADR-025)</b>: {@link #prepare(Long)} 는 소스 이미지 검증 통과 후·분산 락
 * 진입 전·과금 {@code POST /task} 전에 {@code admitOrPark} 게이트를 탄다 — 활성 작업
 * (PREPARING+GENERATING) 이 cap 이상이면 {@code tripo:queue} 에 park 하고 REQUESTED 유지한 채
 * 정상 반환한다(Worker 가 markProcessed → Kafka 컷). 재개는 drainer 의 Kafka 재발행
 * (bypassAdmission=true) 과 Reconcile REQUESTED-stranded 복구다. 드레이너 재발행분은
 * {@link #prepareFromAdmissionQueue(Long, long)} 로 진입해 <b>게이트를 다시 타지 않는다</b>
 * (FIFO score 리셋 방지). 게이트/검증/전이 코어는 {@code doPrepare} 로 공유한다.</p>
 *
 * <p><b>설계 결정 #5 (예외 전파 규칙)</b>:</p>
 * <ul>
 *   <li>Tripo 호출 <b>전</b> 실패: 예외 throw → Worker 가 release() 후 Kafka 재시도 (무과금)</li>
 *   <li>Tripo 호출 <b>후</b> 실패 (TX2 DB 오류 등): 예외 삼키고 return → release 하지 않음
 *       → pending_task 레코드가 남아 {@code Reconcile(P1-G).recoverPreparing} 이
 *       {@code markGenerating} 으로 복구. pending 도 없는 경우 (선저장 자체 실패)
 *       {@code markFailed(TX2_DB_FAILED)} — Tripo cancel API 미지원으로 stranded task 의
 *       1회분 크레딧은 영구 손실 (ADR-011 v1.1 §④)</li>
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
    /**
     * TX2 를 단일 트랜잭션으로 묶기 위한 헬퍼 빈. 같은 클래스 내부 {@code @Transactional} 메서드
     * 호출은 Spring AOP 프록시가 우회되므로 별도 빈으로 분리한다 (설계 §7 [6], 2026-04-28 사고).
     */
    private final PrepareWorkflowTxHelper txHelper;
    /**
     * TX2 성공 후 {@link WorkflowGeneratingConfirmedEvent} 를 발행한다. 실제 Redis DelayedQueue
     * offer 는 {@code WorkflowGeneratingConfirmedEventListener} 가 {@code AFTER_COMMIT} 경계에서
     * 수행 — Redis disabled 환경은 Bean 자체가 없어 no-op.
     */
    private final ApplicationEventPublisher eventPublisher;
    /** 입장 큐 (ADR-025) — cap 초과 시 park 대상 ZSET 포트. */
    private final TripoAdmissionQueuePort admissionQueuePort;
    /** 입장 게이트 설정 (ADR-025) — cap / 활성화 스위치. */
    private final AdmissionQueueProperties admissionProperties;
    /** 입장 큐 관측 지표 (ADR-025 Counters) — application→micrometer 직접 의존 회피용 포트. */
    private final AdmissionMetricsPort admissionMetricsPort;

    @Override
    public void prepare(Long workflowId) {
        WorkflowSnapshot snapshot = loadAndValidate(workflowId);
        if (snapshot == null) {
            return;
        }
        // 이미 진행/종결된 워크플로우(중복 소비·지연 재처리·post-TX1 Tripo Retryable 재전송 등)는
        // 게이트에 태우지 않는다. 태우면 cap 도달 시 admitOrPark 가 비-REQUESTED 워크플로우를
        // tripo:queue 에 park → 드레이너가 pop·findSnapshot 후 discard 하는 큐 오염이 발생한다.
        // 드레이너의 step≠REQUESTED discard 가드와 대칭으로 소스에서 차단한다(방어 심층).
        if (snapshot.currentStep() != WorkflowStep.REQUESTED) {
            log.info("prepare 건너뜀 — 이미 진행/종결(다른 경로 처리됨). workflowId: {}, currentStep: {}",
                    workflowId, snapshot.currentStep());
            return;
        }
        // 입장 게이트 (ADR-025): validate 통과 후·분산 락 전·과금 POST 전.
        if (!admitOrPark(workflowId)) {
            return;
        }
        doPrepare(workflowId, snapshot, null);
    }

    @Override
    public void prepareFromAdmissionQueue(Long workflowId, long dispatchEpochMillis) {
        WorkflowSnapshot snapshot = loadAndValidate(workflowId);
        if (snapshot == null) {
            return;
        }
        // 게이트 미경유 (ADR-025): 이미 큐에서 FIFO 선발된 항목 — 재park score 리셋 방지.
        doPrepare(workflowId, snapshot, dispatchEpochMillis);
    }

    /**
     * findSnapshot + 영구 실패 검증(소스 이미지)을 공유 수행한다 (순수 추출).
     *
     * <p>영구 실패 검증을 락 밖에서 먼저 수행한다. 실패는 재시도해도 결과가 같은 케이스
     * (이미지 4장 미달 / S3 객체 누락) 이므로 락 진입 비용을 아끼고, PREPARING 일시 전이 없이
     * REQUESTED → FAILED 로 직행해 Reconcile 의 PREPARING stuck 오판 가능성도 줄인다.</p>
     *
     * @return 진행 가능하면 스냅샷, 조회 실패/검증 실패면 {@code null}
     */
    private WorkflowSnapshot loadAndValidate(Long workflowId) {
        Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(workflowId);
        if (snapshot.isEmpty()) {
            log.warn("워크플로우 조회 실패 - 무시 - workflowId: {}", workflowId);
            return null;
        }
        if (!validateSourceImages(workflowId, snapshot.get().showcaseId())) {
            return null;
        }
        return snapshot.get();
    }

    /**
     * 입장 게이트 (ADR-025). {@code enabled=false} 면 항상 통과(롤백 스위치). 활성 작업 수
     * ({@code current_step IN (PREPARING,GENERATING)}) 가 cap 미만이면 통과, 이상이면
     * {@code tripo:queue} 에 park 하고 {@code false} 반환(호출자 정상 return → Worker
     * markProcessed → Kafka 컷). check(countActive)-then-act(전이) 가 원자적이지 않아 동시
     * Worker race 로 cap 을 소폭 초과할 수 있다 — ADR-025 soft cap 수용
     * ({@code tripo:semaphore} 가 Tripo API rate 를 하드 제한).
     *
     * @return {@code true}=입장 허용(계속 진행), {@code false}=park 됨(호출자 return)
     */
    private boolean admitOrPark(Long workflowId) {
        if (!admissionProperties.enabled()) {
            return true;
        }
        long active = workflowPort.countActive();
        if (active < admissionProperties.maxConcurrent()) {
            return true;
        }
        admissionQueuePort.parkIfAbsent(workflowId, System.currentTimeMillis());
        admissionMetricsPort.parkOccurred();
        log.info("동시 생성 cap({}) 도달 — tripo:queue park, REQUESTED 유지. workflowId: {}, active: {}",
                admissionProperties.maxConcurrent(), workflowId, active);
        return false;
    }

    /**
     * 검증·게이트 통과 후 코어 흐름 (TX1 → Tripo → pending → TX2 → poll offer). 로직은
     * 분리 전과 동일하며 추출만 했다.
     *
     * @param dispatchEpochMillis drained(bypass) 경로면 드레이너 재발행 시각, 일반 경로면
     *                            {@code null}. in-flight latency 측정(ADR-025 NN7) 과 N1
     *                            중복 디스패치 관측(bypassSkipped) 분기 판정에만 쓰인다.
     */
    private void doPrepare(Long workflowId, WorkflowSnapshot snapshot, Long dispatchEpochMillis) {
        Long showcaseId = snapshot.showcaseId();

        if (!transitionToPreparingUnderLock(workflowId)) {
            // bypass(drained) 경로에서 affected=0 = 다른 경로가 이미 진행 = N1 중복 디스패치 관측.
            if (dispatchEpochMillis != null) {
                admissionMetricsPort.bypassSkipped();
            }
            return;
        }
        // drained 경로만: 재발행 시각 → 입장까지의 지연 기록 (NN7).
        if (dispatchEpochMillis != null) {
            admissionMetricsPort.recordInflight(dispatchEpochMillis);
        }

        String tripoTaskId = callTripoOrHandle(workflowId, showcaseId);
        if (tripoTaskId == null) {
            // 이미 내부 분기에서 FAILED 처리했거나 재시도용 예외를 재전파했다.
            return;
        }

        if (!preservePendingTask(workflowId, tripoTaskId)) {
            return;
        }

        if (transitionToGeneratingUnderLock(workflowId, tripoTaskId)) {
            enqueueForPolling(workflowId);
        }
    }

    /**
     * TX2 성공 후 {@link WorkflowGeneratingConfirmedEvent} 를 발행한다.
     *
     * <p>실제 Redis DelayedQueue offer 는
     * {@code WorkflowGeneratingConfirmedEventListener} 가 {@code AFTER_COMMIT} 단계에서 수행한다.
     * 이 패턴의 장점: (1) Worker 서비스가 Redis Port 를 직접 몰라도 됨 (2) 향후 상위 TX 가 추가돼도
     * "DB 커밋 후 offer" 계약이 자동 유지됨.</p>
     */
    private void enqueueForPolling(Long workflowId) {
        eventPublisher.publishEvent(new WorkflowGeneratingConfirmedEvent(workflowId));
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
     *
     * <p><b>락 잡기 전에 호출된다</b>: 두 실패 케이스 모두 재시도해도 결과가 같은 영구 실패라
     * 락 안에서 검증할 의미가 없다. {@link #markFailedOrLog} 자체가 조건부 UPDATE
     * ({@code WHERE current_step NOT IN (COMPLETED, FAILED)}) 라 락 없이도 동시 호출이 안전하다.</p>
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
     *
     * <p><b>재시도 동작</b>: 재시도 진입 시 {@code transitionToPreparingUnderLock} 가
     * affected=0 으로 컷되어 두 번째 Tripo POST 는 발생하지 않는다. 다만 이미 발생한 Tripo
     * task 는 task_id 가 유실된 채 백그라운드에서 완료되며,
     * {@code Reconcile.recoverPreparing} 이 PREPARING heartbeat 임계 (운영 기본 60s,
     * {@code app.reconcile.preparing-stuck-seconds} 설정값) 경과 후 {@code markFailed(TX2_DB_FAILED)}
     * + ALERT 로 종결한다.</p>
     *
     * <p><b>잔존 손실</b>: Tripo {@code cancel} API 미지원 (2026-04-28 재확인) 으로 stranded
     * task 의 자동 회수 경로가 없어 1회분 크레딧이 영구 손실된다. 운영 측 일일 balance 모니터링
     * + {@code failure_code=TX2_DB_FAILED} 카운터로 사후 인지한다 (ADR-011 v1.1 §④,
     * 설계 v1.2 §4 ④ 참조).</p>
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
     *
     * @return TX2 조건부 UPDATE 가 affected=1 로 성공했으면 {@code true} — 호출자는
     *         이 때만 Poller DelayedQueue 에 offer 해야 한다 (Redis 호출은 락 밖).
     */
    private boolean transitionToGeneratingUnderLock(Long workflowId, String tripoTaskId) {
        AtomicBoolean transitioned = new AtomicBoolean(false);
        try {
            workflowLockPort.withLock(workflowId, () -> {
                // TX2: markGenerating + tripo_pending_task DELETE 를 단일 트랜잭션으로 수행 (설계 §7 [6]).
                // helper 호출은 Spring AOP 프록시 경유 → @Transactional 적용 보장.
                int affected = txHelper.executeTx2(workflowId, tripoTaskId);
                if (affected == 0) {
                    log.warn("TX2 affected=0 — 다른 Worker/Reconcile 이 이미 전이한 것으로 추정. "
                                    + "pending 유지 (Reconcile 이 정리). workflowId: {}, taskId: {}",
                            workflowId, tripoTaskId);
                    return;
                }
                transitioned.set(true);
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
        return transitioned.get();
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
