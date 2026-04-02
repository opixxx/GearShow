package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import com.gearshow.backend.showcase.application.dto.CommentResult;
import com.gearshow.backend.showcase.application.port.in.ListCommentsUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 댓글 목록 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class ListCommentsService implements ListCommentsUseCase {

    /** 댓글 조회가 허용되는 쇼케이스 상태 */
    private static final Set<ShowcaseStatus> COMMENTABLE_STATUSES =
            Set.of(ShowcaseStatus.ACTIVE, ShowcaseStatus.SOLD);

    private final ShowcasePort showcasePort;
    private final ShowcaseCommentPort showcaseCommentPort;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<CommentResult> list(Long showcaseId, String pageToken, int size) {
        validateShowcaseVisible(showcaseId);

        List<ShowcaseComment> comments;
        if (pageToken == null) {
            comments = showcaseCommentPort.findByShowcaseIdFirstPage(showcaseId, size);
        } else {
            Pair<Instant, Long> cursor = PageTokenUtil.decode(pageToken, Instant.class, Long.class);
            comments = showcaseCommentPort.findByShowcaseIdWithCursor(
                    showcaseId, cursor.getLeft(), cursor.getRight(), size);
        }

        List<CommentResult> results = comments.stream()
                .map(CommentResult::from)
                .toList();

        return PageInfo.of(results, size,
                CommentResult::createdAt,
                CommentResult::showcaseCommentId);
    }

    /**
     * 쇼케이스가 존재하고 댓글 조회 가능한 상태인지 검증한다.
     */
    private void validateShowcaseVisible(Long showcaseId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);

        if (!COMMENTABLE_STATUSES.contains(showcase.getStatus())) {
            throw new NotFoundShowcaseException();
        }
    }
}
