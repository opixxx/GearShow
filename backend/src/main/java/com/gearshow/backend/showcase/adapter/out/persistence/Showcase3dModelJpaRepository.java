package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * 상태를 원자적으로 전환한다. 현재 상태가 expected 일 때만 newStatus 로 변경한다.
     *
     * <p>동시에 여러 Worker 가 같은 모델을 처리하려 할 때, DB 레벨에서 1명만 성공하도록 보장한다.
     * {@code WHERE model_status = :expected} 조건 덕분에 이미 전환된 행은 영향 row 0 을 반환한다.</p>
     *
     * @return 영향 받은 행 수 (1=성공, 0=이미 다른 Worker 가 전환함)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Showcase3dModelJpaEntity e SET e.modelStatus = :newStatus" +
            " WHERE e.id = :id AND e.modelStatus = :expected")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expected") ModelStatus expected,
                              @Param("newStatus") ModelStatus newStatus);

    /**
     * 쇼케이스 ID로 3D 모델을 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 3D 모델 JPA 엔티티 Optional
     */
    Optional<Showcase3dModelJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID 기반 존재 여부 확인. Spring Data JPA 가 {@code SELECT 1} 로 최적화한다.
     */
    boolean existsByShowcaseId(Long showcaseId);

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
     * 특정 상태이면서 {@code requestedAt} 이 {@code referenceAt} 이전인 모델을 조회한다.
     * REQUESTED stuck 감지용. {@code (model_status, requested_at)} 인덱스로 커버된다.
     */
    @Query("SELECT m FROM Showcase3dModelJpaEntity m" +
            " WHERE m.modelStatus = :status" +
            "   AND m.requestedAt < :referenceAt" +
            " ORDER BY m.requestedAt ASC")
    List<Showcase3dModelJpaEntity> findByStatusAndRequestedBefore(
            @Param("status") ModelStatus status,
            @Param("referenceAt") Instant referenceAt,
            Pageable pageable);

    /**
     * GENERATING 상태이면서 {@code generation_task_id} 가 비어있고 오래된 좀비 모델을 조회한다.
     * Worker 가 tryAcquire 후 Tripo createTask 도달 전에 크래시한 edge case 복구용.
     *
     * <p>in-memory 필터로 정상 모델을 후처리 제거하는 방식은 batch 낭비가 심하므로
     * 쿼리 레벨에서 직접 {@code generationTaskId IS NULL} 조건을 적용한다.</p>
     */
    @Query("SELECT m FROM Showcase3dModelJpaEntity m" +
            " WHERE m.modelStatus = com.gearshow.backend.showcase.domain.vo.ModelStatus.GENERATING" +
            "   AND m.generationTaskId IS NULL" +
            "   AND m.requestedAt < :referenceAt" +
            " ORDER BY m.requestedAt ASC")
    List<Showcase3dModelJpaEntity> findStaleGeneratingWithoutTaskId(
            @Param("referenceAt") Instant referenceAt,
            Pageable pageable);

    /**
     * Downloader TX_final 용 — {@code showcase_id} 기반으로 완성 URL 3종을 직접 UPDATE 한다 (P1-F).
     *
     * <p>상태 머신 전이 검증은 ADR-010 을 따라 프로세스 진실성 원본({@code model_generation_workflow})
     * 에서 수행되므로 여기서는 순수 UPDATE (행 없으면 affected=0) 를 담당. {@code model_status=COMPLETED}
     * 세팅은 레거시 경로 호환용이며 P1-G 에서 프로세스 컬럼 축소 시 함께 제거된다 (Javadoc 에
     * transitional 성격 명시).</p>
     *
     * <p>WHERE 절 {@code model_status <> COMPLETED} — ADR-012 조건부 UPDATE 원칙 적용.
     * 다른 경로(수동 UPDATE 등) 로 이미 COMPLETED 된 행을 덮어쓰지 않는다.</p>
     *
     * @deprecated P1-G 에서 프로세스 컬럼 축소와 함께 제거 예정 (ADR-010). 신규 호출 경로 추가 금지.
     */
    @Deprecated(forRemoval = true)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Showcase3dModelJpaEntity e
               SET e.modelFileUrl = :modelFileUrl,
                   e.previewImageUrl = :previewImageUrl,
                   e.generatedAt = :generatedAt,
                   e.modelStatus = com.gearshow.backend.showcase.domain.vo.ModelStatus.COMPLETED
             WHERE e.showcaseId = :showcaseId
               AND e.modelStatus <> com.gearshow.backend.showcase.domain.vo.ModelStatus.COMPLETED
            """)
    int markCompletedByShowcaseId(@Param("showcaseId") Long showcaseId,
                                  @Param("modelFileUrl") String modelFileUrl,
                                  @Param("previewImageUrl") String previewImageUrl,
                                  @Param("generatedAt") Instant generatedAt);
}
