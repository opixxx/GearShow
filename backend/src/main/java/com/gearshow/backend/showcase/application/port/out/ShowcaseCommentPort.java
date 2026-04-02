package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ShowcaseComment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 쇼케이스 댓글 Outbound Port.
 */
public interface ShowcaseCommentPort {

    ShowcaseComment save(ShowcaseComment comment);

    Optional<ShowcaseComment> findById(Long id);

    /**
     * 첫 페이지 댓글 목록을 조회한다.
     */
    List<ShowcaseComment> findByShowcaseIdFirstPage(Long showcaseId, int size);

    /**
     * 커서 기반 댓글 목록을 조회한다 (createdAt DESC, id DESC).
     */
    List<ShowcaseComment> findByShowcaseIdWithCursor(Long showcaseId,
                                                      Instant cursorCreatedAt, Long cursorId,
                                                      int size);

    /**
     * 쇼케이스 ID로 댓글 개수를 조회한다 (ACTIVE 상태만).
     */
    int countActiveByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스의 ACTIVE 댓글 개수를 일괄 조회한다.
     *
     * @param showcaseIds 쇼케이스 ID 목록
     * @return showcaseId → commentCount 매핑
     */
    Map<Long, Integer> countActiveByShowcaseIds(List<Long> showcaseIds);
}
