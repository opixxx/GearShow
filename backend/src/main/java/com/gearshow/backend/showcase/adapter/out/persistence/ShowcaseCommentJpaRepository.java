package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쇼케이스 댓글 JPA 저장소.
 */
public interface ShowcaseCommentJpaRepository extends JpaRepository<ShowcaseCommentJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 댓글 목록을 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 댓글 JPA 엔티티 목록
     */
    List<ShowcaseCommentJpaEntity> findByShowcaseId(Long showcaseId);
}
