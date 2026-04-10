package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 3D 모델 JPA 저장소.
 */
public interface Showcase3dModelJpaRepository extends JpaRepository<Showcase3dModelJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 3D 모델을 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 3D 모델 JPA 엔티티 Optional
     */
    Optional<Showcase3dModelJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스 중 3D 모델이 존재하는 쇼케이스 ID를 조회한다.
     */
    @Query("SELECT m.showcaseId FROM Showcase3dModelJpaEntity m" +
            " WHERE m.showcaseId IN :showcaseIds")
    List<Long> findShowcaseIdsByShowcaseIds(@Param("showcaseIds") List<Long> showcaseIds);

    /**
     * 폴링 대상이 되는 GENERATING 모델을 조회한다.
     * Tripo task_id 가 설정된 모델만 반환한다 (task_id 가 없으면 폴링 불가).
     *
     * <p>{@code last_polled_at} 이 null 인 레코드가 먼저 나오도록 정렬하여,
     * 아직 한 번도 폴링하지 않은 신규 task 를 우선 처리한다.</p>
     */
    @Query("SELECT m FROM Showcase3dModelJpaEntity m" +
            " WHERE m.modelStatus = com.gearshow.backend.showcase.domain.vo.ModelStatus.GENERATING" +
            "   AND m.generationTaskId IS NOT NULL" +
            " ORDER BY m.lastPolledAt ASC NULLS FIRST, m.id ASC")
    List<Showcase3dModelJpaEntity> findPollableGeneratingTasks(Pageable pageable);

    /**
     * 특정 상태이면서 {@code referenceAt} 이전에 가장 최근 변경된 모델을 조회한다.
     * stuck task 감지 / recovery 스케줄러가 사용한다.
     *
     * <p>"변경 시각" 의 정의:</p>
     * <ul>
     *   <li>GENERATING: {@code last_polled_at} 이 null 이면 {@code requested_at}, 아니면 {@code last_polled_at}</li>
     *   <li>그 외: {@code requested_at} 을 기준으로 함</li>
     * </ul>
     *
     * <p>코드 단순화를 위해 쿼리 레벨에서는 {@code requested_at} 기준으로만 필터링하고,
     * 호출 측에서 {@code lastPolledAt} 을 추가로 확인한다.</p>
     */
    @Query("SELECT m FROM Showcase3dModelJpaEntity m" +
            " WHERE m.modelStatus = :status" +
            "   AND m.requestedAt < :referenceAt" +
            " ORDER BY m.requestedAt ASC")
    List<Showcase3dModelJpaEntity> findByStatusAndRequestedBefore(
            @Param("status") ModelStatus status,
            @Param("referenceAt") Instant referenceAt,
            Pageable pageable);
}
