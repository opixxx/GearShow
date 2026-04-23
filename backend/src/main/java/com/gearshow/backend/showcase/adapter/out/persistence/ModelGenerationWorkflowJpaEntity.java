package com.gearshow.backend.showcase.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 3D 모델 생성 파이프라인 워크플로우 JPA 엔티티.
 *
 * <p>도메인 산출물 테이블 {@code showcase_3d_model} 과 분리되어, 파이프라인의 생명주기
 * (재시도 · 상태 전이 · 폴링 · 실패 분류) 를 전담한다. 재시도는 같은 {@code showcase_id} 에
 * 새 행을 INSERT 하여 {@code attempt_no} 를 증가시키는 방식으로 이력을 보존한다.</p>
 *
 * <p><b>설계 근거</b>: ADR-010 (도메인/프로세스 테이블 분리) · ADR-011 (멱등성) · ADR-012 (동시성).</p>
 *
 * <p><b>인덱스</b>:</p>
 * <ul>
 *   <li>{@code idx_mgw_step_heartbeat}: Reconcile 배치가 heartbeat 기반 stuck 을 감지할 때 사용.</li>
 *   <li>{@code idx_mgw_step_tripo_succeeded}: GENERATING 내부에서 Tripo 처리 중 / S3 미러링 중 서브-단계를 구분해 각기 다른 stuck 임계값 적용.</li>
 *   <li>{@code idx_mgw_step_last_polled}: Poller 의 폴링 대상 조회 및 폴링 루프 유실 감지.</li>
 *   <li>{@code idx_mgw_showcase_attempt}: 재시도 이력 조회 (가장 최근 attempt 찾기).</li>
 * </ul>
 */
@Entity
@Table(
        name = "model_generation_workflow",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mgw_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                // P1-B-γ 리뷰 대응: 서로 다른 idempotency_key 로 같은 쇼케이스에 동시 재시도가 들어오면
                // nextAttemptNo → saveRequested 사이 race 로 동일 attempt_no 가 중복 INSERT 될 수 있다.
                // DB 레벨 UNIQUE 로 직렬화를 강제해 재시도 이력 무결성을 보장한다.
                @UniqueConstraint(
                        name = "uk_mgw_showcase_attempt",
                        columnNames = {"showcase_id", "attempt_no"}
                )
        },
        indexes = {
                @Index(name = "idx_mgw_step_heartbeat",
                        columnList = "current_step, heartbeat_at"),
                @Index(name = "idx_mgw_step_tripo_succeeded",
                        columnList = "current_step, tripo_succeeded_at"),
                @Index(name = "idx_mgw_step_last_polled",
                        columnList = "current_step, last_polled_at")
                // 기존 idx_mgw_showcase_attempt 는 uk_mgw_showcase_attempt UNIQUE 가 동일 prefix 를
                // 커버하므로 삭제했다 — UNIQUE 인덱스가 조회 인덱스 역할을 겸한다.
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelGenerationWorkflowJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "showcase_id", nullable = false)
    private Long showcaseId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 16)
    private CurrentStep currentStep;

    @Column(name = "tripo_task_id", length = 64)
    private String tripoTaskId;

    /** Tripo 응답 헤더 {@code X-Tripo-Trace-ID} 저장. 장애 발생 시 Tripo 측 문의에 필수. */
    @Column(name = "tripo_trace_id", length = 64)
    private String tripoTraceId;

    /**
     * Tripo SUCCESS 인지 시각. {@code current_step=GENERATING} 내부에서
     * {@code NULL} = Tripo 처리 중, {@code NOT NULL} = S3 미러링 중으로 서브-단계를 구분한다.
     */
    @Column(name = "tripo_succeeded_at")
    private Instant tripoSucceededAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "failure_source", length = 32)
    private String failureSource;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    /** 워커가 긴 외부 I/O 진행 중 주기적으로 갱신하여 Reconcile 의 stuck 오판을 방지한다. */
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ModelGenerationWorkflowJpaEntity(Long id, Long showcaseId, String idempotencyKey,
                                             int attemptNo, CurrentStep currentStep,
                                             String tripoTaskId, String tripoTraceId,
                                             Instant tripoSucceededAt,
                                             int retryCount, String failureCode,
                                             String failureMessage, String failureSource,
                                             Instant lastPolledAt, Instant heartbeatAt,
                                             Instant startedAt, Instant finishedAt,
                                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.idempotencyKey = idempotencyKey;
        this.attemptNo = attemptNo;
        this.currentStep = currentStep;
        this.tripoTaskId = tripoTaskId;
        this.tripoTraceId = tripoTraceId;
        this.tripoSucceededAt = tripoSucceededAt;
        this.retryCount = retryCount;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failureSource = failureSource;
        this.lastPolledAt = lastPolledAt;
        this.heartbeatAt = heartbeatAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 신규 워크플로우를 {@link CurrentStep#REQUESTED} 상태로 생성한다.
     * 재시도는 {@code attemptNo} 를 증가시켜 같은 {@code showcaseId} 로 새 엔티티를 만든다.
     */
    public static ModelGenerationWorkflowJpaEntity requested(Long showcaseId,
                                                             String idempotencyKey,
                                                             int attemptNo) {
        Instant now = Instant.now();
        return ModelGenerationWorkflowJpaEntity.builder()
                .showcaseId(showcaseId)
                .idempotencyKey(idempotencyKey)
                .attemptNo(attemptNo)
                .currentStep(CurrentStep.REQUESTED)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public enum CurrentStep {
        REQUESTED,
        PREPARING,
        GENERATING,
        COMPLETED,
        FAILED
    }
}
