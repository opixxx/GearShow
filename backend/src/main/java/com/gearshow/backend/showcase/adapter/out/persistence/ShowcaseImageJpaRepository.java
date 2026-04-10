package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 이미지 JPA 저장소.
 *
 * <p>인덱스 권장 (DB에 직접 추가 예정):</p>
 * <ul>
 *     <li>{@code (showcase_id, sort_order)} — findByShowcaseId, findFirstByShowcaseIdOrderBySortOrder 커버링</li>
 *     <li>{@code (showcase_id, is_primary)} — findPrimaryByShowcaseId 커버링</li>
 * </ul>
 */
public interface ShowcaseImageJpaRepository extends JpaRepository<ShowcaseImageJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 이미지 목록을 조회한다.
     * 인덱스 권장: {@code (showcase_id, sort_order)}
     */
    List<ShowcaseImageJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID로 이미지 개수를 조회한다.
     * 인덱스 권장: {@code (showcase_id)}
     */
    int countByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID로 대표 이미지를 조회한다.
     * 인덱스 권장: {@code (showcase_id, is_primary)}
     */
    @Query("SELECT si FROM ShowcaseImageJpaEntity si" +
            " WHERE si.showcaseId = :showcaseId AND si.primary = true")
    Optional<ShowcaseImageJpaEntity> findPrimaryByShowcaseId(@Param("showcaseId") Long showcaseId);

    /**
     * 여러 쇼케이스의 대표 이미지를 일괄 조회한다.
     * 인덱스 권장: {@code (showcase_id, is_primary)}
     */
    @Query("SELECT si FROM ShowcaseImageJpaEntity si" +
            " WHERE si.showcaseId IN :showcaseIds AND si.primary = true")
    List<ShowcaseImageJpaEntity> findPrimaryByShowcaseIds(@Param("showcaseIds") List<Long> showcaseIds);

    /**
     * 쇼케이스 ID로 가장 정렬 순서가 낮은 이미지 1건을 조회한다.
     * 대표 이미지 삭제 후 다음 대표 후보를 찾을 때 사용한다.
     * 인덱스 권장: {@code (showcase_id, sort_order)} — 커버링 인덱스로 LIMIT 1 최적화
     */
    @Query("SELECT si FROM ShowcaseImageJpaEntity si" +
            " WHERE si.showcaseId = :showcaseId" +
            " ORDER BY si.sortOrder ASC")
    List<ShowcaseImageJpaEntity> findFirstByShowcaseIdOrderBySortOrder(
            @Param("showcaseId") Long showcaseId, PageRequest pageRequest);

    /**
     * 쇼케이스 ID로 가장 정렬 순서가 낮은 이미지 1건을 Optional로 반환한다.
     * Hibernate JPQL이 LIMIT 절을 직접 지원하지 않으므로 PageRequest로 1건만 가져온다.
     */
    default Optional<ShowcaseImageJpaEntity> findFirstByShowcaseIdOrderBySortOrder(Long showcaseId) {
        return findFirstByShowcaseIdOrderBySortOrder(showcaseId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }
}
