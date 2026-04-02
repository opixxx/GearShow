package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 이미지 JPA 저장소.
 */
public interface ShowcaseImageJpaRepository extends JpaRepository<ShowcaseImageJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 이미지 목록을 조회한다.
     */
    List<ShowcaseImageJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID로 이미지 개수를 조회한다.
     */
    int countByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID로 대표 이미지를 조회한다.
     */
    @Query("SELECT si FROM ShowcaseImageJpaEntity si" +
            " WHERE si.showcaseId = :showcaseId AND si.primary = true")
    Optional<ShowcaseImageJpaEntity> findPrimaryByShowcaseId(@Param("showcaseId") Long showcaseId);

    /**
     * 여러 쇼케이스의 대표 이미지를 일괄 조회한다.
     */
    @Query("SELECT si FROM ShowcaseImageJpaEntity si" +
            " WHERE si.showcaseId IN :showcaseIds AND si.primary = true")
    List<ShowcaseImageJpaEntity> findPrimaryByShowcaseIds(@Param("showcaseIds") List<Long> showcaseIds);
}
