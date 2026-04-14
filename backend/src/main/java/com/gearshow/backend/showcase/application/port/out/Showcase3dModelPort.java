package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 3D 모델 Outbound Port.
 */
public interface Showcase3dModelPort {

    /**
     * 3D 모델을 저장한다.
     */
    Showcase3dModel save(Showcase3dModel model);

    /**
     * 현재 상태가 expected 일 때만 newStatus 로 원자적 전환한다.
     *
     * <p>동시에 여러 Worker 가 같은 모델을 처리하려 할 때 DB 레벨에서 1명만 성공하도록 보장한다.
     * {@code UPDATE ... WHERE model_status = :expected} 조건으로 이미 전환된 행은 영향 0 을 반환.</p>
     *
     * @return 영향 받은 행 수 (1=성공, 0=다른 Worker 가 이미 전환)
     */
    int updateStatusIfCurrent(Long id, ModelStatus expected, ModelStatus newStatus);

    /**
     * ID로 3D 모델을 조회한다.
     */
    Optional<Showcase3dModel> findById(Long id);

    /**
     * 쇼케이스 ID로 3D 모델을 조회한다.
     */
    Optional<Showcase3dModel> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID에 해당하는 3D 모델 존재 여부를 확인한다.
     */
    boolean existsByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스에 대해 3D 모델이 존재하는 쇼케이스 ID 목록을 반환한다.
     *
     * @param showcaseIds 쇼케이스 ID 목록
     * @return 3D 모델이 존재하는 쇼케이스 ID Set
     */
    java.util.Set<Long> findShowcaseIdsWithModel(java.util.List<Long> showcaseIds);

    /**
     * Tripo task_id 가 설정된 GENERATING 상태의 모델을 배치로 조회한다.
     * 폴링 스케줄러가 사용한다.
     *
     * @param limit 최대 반환 수
     */
    List<Showcase3dModel> findPollableGeneratingTasks(int limit);

    /**
     * 지정 상태이면서 {@code requestedAt} 이 {@code referenceAt} 이전인 모델을 조회한다.
     * REQUESTED stuck 감지용.
     *
     * @param status      대상 상태
     * @param referenceAt 이 시각 이전에 요청된 모델만 조회
     * @param limit       최대 반환 수
     */
    List<Showcase3dModel> findStaleByStatus(ModelStatus status, Instant referenceAt, int limit);

    /**
     * GENERATING 상태이면서 {@code generationTaskId} 가 비어있고 오래된 좀비 모델을 조회한다.
     * Worker 가 Tripo 호출 전 크래시한 edge case 복구용.
     *
     * @param referenceAt 이 시각 이전에 요청된 모델만 조회
     * @param limit       최대 반환 수
     */
    List<Showcase3dModel> findStaleGeneratingWithoutTaskId(Instant referenceAt, int limit);
}
