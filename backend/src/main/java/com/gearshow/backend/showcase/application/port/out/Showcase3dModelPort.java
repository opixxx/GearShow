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
