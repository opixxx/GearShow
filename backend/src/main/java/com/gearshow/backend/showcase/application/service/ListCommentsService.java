package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import com.gearshow.backend.showcase.application.dto.CommentResult;
import com.gearshow.backend.showcase.application.port.in.ListCommentsUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 댓글 목록 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class ListCommentsService implements ListCommentsUseCase {

    private final ShowcaseCommentPort showcaseCommentPort;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<CommentResult> list(Long showcaseId, String pageToken, int size) {
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
}
