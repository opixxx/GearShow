package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 3D 모델 소스 이미지 JPA 저장소 (ADR-010 showcase_id 직접 참조).
 */
public interface ModelSourceImageJpaRepository extends JpaRepository<ModelSourceImageJpaEntity, Long> {

    List<ModelSourceImageJpaEntity> findByShowcaseIdOrderBySortOrderAsc(Long showcaseId);

    int countByShowcaseId(Long showcaseId);

    @Query("""
            SELECT m.imageUrl
              FROM ModelSourceImageJpaEntity m
             WHERE m.showcaseId = :showcaseId
             ORDER BY m.sortOrder ASC
            """)
    List<String> findImageUrlsByShowcaseId(@Param("showcaseId") Long showcaseId);

    /**
     * 쇼케이스 ID 로 소스 이미지를 모두 hard delete 한다 (재시도 시 옛 4장 정리 + 새 4장 INSERT 용도).
     *
     * <p>JPQL bulk DELETE 로 단일 쿼리를 보장한다 — Spring Data 의 derived {@code deleteByXxx} 는
     * 기본적으로 SELECT → 각 행 {@code EntityManager.remove()} 패턴이라 1+N 쿼리 위험이 있어
     * 회피한다. {@code clearAutomatically=true, flushAutomatically=true} 를 명시해 같은 TX 안에서
     * 후속 {@code saveAll(4행)} 시 영속성 컨텍스트의 stale 엔티티가 충돌하지 않도록 한다
     * (프로젝트 내 다른 {@code @Modifying} 컨벤션과도 일치).</p>
     *
     * <p><b>트랜잭션</b>: 호출처({@code RequestModelGenerationService.resetSourceImagesAndRequestRetry})
     * 가 {@code @Transactional} 메서드이므로 상위 TX 에 참여한다 — 어댑터 레벨에 별도
     * {@code @Transactional} 을 두면 application 계층의 트랜잭션 경계가 흐려져 명시적으로
     * 두지 않는다.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ModelSourceImageJpaEntity m WHERE m.showcaseId = :showcaseId")
    void deleteByShowcaseId(@Param("showcaseId") Long showcaseId);
}
