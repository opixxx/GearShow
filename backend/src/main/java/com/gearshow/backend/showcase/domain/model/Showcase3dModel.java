package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidGenerationTaskIdException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseModelStatusTransitionException;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 쇼케이스 3D 모델 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 쇼케이스당 최대 1개만 존재한다.</p>
 *
 * <p><b>상태 선행 전환 아키텍처</b>: Worker 는 Tripo 호출 전에 PREPARING 상태로 먼저 전환하여
 * "내가 이제 외부 API 를 호출할 것이다" 라는 의도를 DB 에 기록한다. 이후 Tripo task 를 생성한
 * 직후 task_id 와 함께 GENERATING 상태로 전환한다.</p>
 *
 * <p><b>retryCount</b>: PREPARING 상태에서 크래시 후 Recovery 가 자동 재시도할 때마다 증가한다.
 * 3회 이상이면 FAILED + Alert 로 전환하여 무한 루프를 방지한다.</p>
 */
@Getter
public class Showcase3dModel {

    private static final int MAX_RETRY_COUNT = 3;

    private final Long id;
    private final Long showcaseId;
    private final String modelFileUrl;
    private final String previewImageUrl;
    private final ModelStatus modelStatus;
    private final String generationProvider;
    /** Tripo task_id — 폴링 스케줄러가 상태 조회에 사용한다. GENERATING 상태에서만 non-null. */
    private final String generationTaskId;
    private final Instant requestedAt;
    private final Instant generatedAt;
    /** 폴링 스케줄러가 마지막으로 Tripo 상태를 확인한 시각 (stuck 감지 기준). */
    private final Instant lastPolledAt;
    private final String failureReason;
    private final Instant createdAt;
    /** Recovery 자동 재시도 횟수. PREPARING 좀비 복구 시 증가하며 MAX_RETRY_COUNT 초과 시 FAILED. */
    private final int retryCount;

    @Builder
    private Showcase3dModel(Long id, Long showcaseId, String modelFileUrl,
                            String previewImageUrl, ModelStatus modelStatus,
                            String generationProvider, String generationTaskId,
                            Instant requestedAt, Instant generatedAt,
                            Instant lastPolledAt, String failureReason,
                            Instant createdAt, int retryCount) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.modelFileUrl = modelFileUrl;
        this.previewImageUrl = previewImageUrl;
        this.modelStatus = modelStatus;
        this.generationProvider = generationProvider;
        this.generationTaskId = generationTaskId;
        this.requestedAt = requestedAt;
        this.generatedAt = generatedAt;
        this.lastPolledAt = lastPolledAt;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.retryCount = retryCount;
    }

    /**
     * 3D 모델 생성을 요청한다.
     *
     * @param showcaseId         쇼케이스 ID
     * @param generationProvider 생성 제공자
     * @return 요청된 3D 모델
     */
    public static Showcase3dModel request(Long showcaseId, String generationProvider) {
        Instant now = Instant.now();
        return Showcase3dModel.builder()
                .showcaseId(showcaseId)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider(generationProvider)
                .requestedAt(now)
                .createdAt(now)
                .retryCount(0)
                .build();
    }

    /**
     * 기존 모델을 REQUESTED 상태로 재설정한다.
     *
     * <p>{@link ModelStatus#FAILED} 또는 {@link ModelStatus#UNAVAILABLE} 상태에서만 가능하다.
     * UNAVAILABLE 허용 사유: Tripo 서비스 장애 복구 후 사용자가 수동으로 재시도할 수 있어야 함.</p>
     *
     * @param generationProvider 생성 제공자
     * @return 재요청된 3D 모델
     */
    public Showcase3dModel resetRequest(String generationProvider) {
        validateStatusTransition(ModelStatus.REQUESTED);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider(generationProvider)
                .requestedAt(Instant.now())
                .createdAt(this.createdAt)
                .retryCount(0)
                .build();
    }

    /**
     * Worker 가 메시지를 잡고 Tripo 호출을 준비하는 PREPARING 상태로 전환한다.
     *
     * <p>이 전이는 {@link ModelStatus#REQUESTED} 상태에서만 허용된다.
     * "외부 API 호출 전에 의도를 기록" 하여 Recovery 스케줄러와 다른 Worker 가
     * 끼어드는 것을 방지한다.</p>
     *
     * <p>PREPARING 상태에서 generationTaskId 는 반드시 NULL 이다.
     * 이는 Tripo 과금이 아직 발생하지 않았음을 보장하며,
     * 이 상태에서의 크래시 복구 시 안전하게 재시도할 수 있는 근거가 된다.</p>
     *
     * @return PREPARING 상태의 3D 모델
     */
    public Showcase3dModel markPreparing() {
        validateStatusTransition(ModelStatus.PREPARING);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.PREPARING)
                .generationProvider(this.generationProvider)
                .requestedAt(this.requestedAt)
                .createdAt(this.createdAt)
                .retryCount(this.retryCount)
                .build();
    }

    /**
     * Tripo task 생성 성공 후 {@link ModelStatus#GENERATING} 상태로 전환한다.
     * task_id 를 함께 저장하여 이후 폴링 스케줄러가 Tripo 상태를 조회할 수 있게 한다.
     *
     * <p>이 전이는 {@link ModelStatus#PREPARING} 상태에서만 허용된다.</p>
     */
    public Showcase3dModel markGenerating(String generationTaskId) {
        if (generationTaskId == null || generationTaskId.isBlank()) {
            throw new InvalidGenerationTaskIdException();
        }
        validateStatusTransition(ModelStatus.GENERATING);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider(this.generationProvider)
                .generationTaskId(generationTaskId)
                .requestedAt(this.requestedAt)
                .createdAt(this.createdAt)
                .retryCount(this.retryCount)
                .build();
    }

    /**
     * 폴링 시각을 업데이트한다. 상태 전이는 없고 {@code lastPolledAt} 만 갱신된다.
     * stuck 감지 스케줄러가 이 값을 기준으로 타임아웃을 판정한다.
     *
     * <p>폴링 메타데이터는 생성 중 모델에만 의미가 있으므로 {@code GENERATING} 상태에서만
     * 호출 가능하다. 그 외 상태에서 호출되면 상태 머신 불변식 위반이다.</p>
     */
    public Showcase3dModel markPolled() {
        if (this.modelStatus != ModelStatus.GENERATING) {
            throw new InvalidShowcaseModelStatusTransitionException();
        }
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelFileUrl(this.modelFileUrl)
                .previewImageUrl(this.previewImageUrl)
                .modelStatus(this.modelStatus)
                .generationProvider(this.generationProvider)
                .generationTaskId(this.generationTaskId)
                .requestedAt(this.requestedAt)
                .generatedAt(this.generatedAt)
                .lastPolledAt(Instant.now())
                .failureReason(this.failureReason)
                .createdAt(this.createdAt)
                .retryCount(this.retryCount)
                .build();
    }

    /**
     * 생성 완료 처리한다.
     *
     * @param modelFileUrl    3D 모델 파일 URL
     * @param previewImageUrl 미리보기 이미지 URL
     * @return 완료된 3D 모델
     */
    public Showcase3dModel complete(String modelFileUrl, String previewImageUrl) {
        validateStatusTransition(ModelStatus.COMPLETED);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelFileUrl(modelFileUrl)
                .previewImageUrl(previewImageUrl)
                .modelStatus(ModelStatus.COMPLETED)
                .generationProvider(this.generationProvider)
                .generationTaskId(this.generationTaskId)
                .requestedAt(this.requestedAt)
                .generatedAt(Instant.now())
                .lastPolledAt(Instant.now())
                .createdAt(this.createdAt)
                .retryCount(this.retryCount)
                .build();
    }

    /**
     * 생성 실패 처리한다. REQUESTED, PREPARING, GENERATING 상태 어디서든 호출 가능하다.
     *
     * @param failureReason 실패 사유
     * @return 실패한 3D 모델
     */
    public Showcase3dModel fail(String failureReason) {
        validateStatusTransition(ModelStatus.FAILED);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.FAILED)
                .generationProvider(this.generationProvider)
                .generationTaskId(this.generationTaskId)
                .requestedAt(this.requestedAt)
                .lastPolledAt(this.lastPolledAt)
                .failureReason(failureReason)
                .createdAt(this.createdAt)
                .retryCount(this.retryCount)
                .build();
    }

    /**
     * 3D 생성 서비스 장애로 일시 이용 불가 상태로 전환한다.
     * Tripo Circuit Breaker 가 OPEN 일 때 호출된다.
     *
     * <p>REQUESTED 또는 PREPARING 상태에서 호출 가능하다.
     * 사용자는 서비스 복구 후 수동으로 재요청할 수 있다
     * ({@link #resetRequest(String)} 가 UNAVAILABLE 전이를 허용한다).</p>
     */
    public Showcase3dModel markUnavailable(String reason) {
        validateStatusTransition(ModelStatus.UNAVAILABLE);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.UNAVAILABLE)
                .generationProvider(this.generationProvider)
                .requestedAt(this.requestedAt)
                .failureReason(reason)
                .createdAt(this.createdAt)
                .retryCount(this.retryCount)
                .build();
    }

    /**
     * PREPARING 좀비 복구: REQUESTED 로 되돌리면서 retryCount 를 증가시킨다.
     *
     * <p>PREPARING + taskId=NULL 상태에서만 호출 가능하다.
     * taskId=NULL 은 Tripo 과금이 발생하지 않았음을 보장하므로 재시도가 안전하다.</p>
     *
     * @param generationProvider 생성 제공자
     * @return retryCount 가 증가된 REQUESTED 상태의 모델
     */
    public Showcase3dModel resetForRetry(String generationProvider) {
        validateStatusTransition(ModelStatus.REQUESTED);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider(generationProvider)
                .requestedAt(Instant.now())
                .createdAt(this.createdAt)
                .retryCount(this.retryCount + 1)
                .build();
    }

    /**
     * 현재 생성 중인지 확인한다.
     *
     * @return 생성 중 여부
     */
    public boolean isGenerating() {
        return this.modelStatus == ModelStatus.GENERATING;
    }

    /**
     * 최대 재시도 횟수를 초과했는지 확인한다.
     *
     * @return 최대 재시도 초과 여부
     */
    public boolean isMaxRetryExceeded() {
        return this.retryCount >= MAX_RETRY_COUNT;
    }

    /**
     * 상태 머신의 단일 진입점. 모든 전이 메서드가 이 검증을 통과해야 실제 전이가 이뤄진다.
     *
     * <p>허용되는 전이:</p>
     * <ul>
     *   <li>REQUESTED → PREPARING | FAILED | UNAVAILABLE</li>
     *   <li>PREPARING → GENERATING | FAILED | UNAVAILABLE | REQUESTED (retryCount 증가 시)</li>
     *   <li>GENERATING → COMPLETED | FAILED</li>
     *   <li>FAILED | UNAVAILABLE → REQUESTED (사용자 재요청)</li>
     *   <li>COMPLETED → (종결, 어디로도 전이 불가)</li>
     * </ul>
     */
    private void validateStatusTransition(ModelStatus target) {
        boolean valid = switch (this.modelStatus) {
            case REQUESTED -> target == ModelStatus.PREPARING
                    || target == ModelStatus.FAILED
                    || target == ModelStatus.UNAVAILABLE;
            case PREPARING -> target == ModelStatus.GENERATING
                    || target == ModelStatus.FAILED
                    || target == ModelStatus.UNAVAILABLE
                    || target == ModelStatus.REQUESTED;
            case GENERATING -> target == ModelStatus.COMPLETED || target == ModelStatus.FAILED;
            case FAILED, UNAVAILABLE -> target == ModelStatus.REQUESTED;
            case COMPLETED -> false;
        };

        if (!valid) {
            throw new InvalidShowcaseModelStatusTransitionException();
        }
    }
}
