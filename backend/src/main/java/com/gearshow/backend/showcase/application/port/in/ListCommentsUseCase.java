package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.CommentResult;

/**
 * 댓글 목록 조회 유스케이스.
 */
public interface ListCommentsUseCase {

    /**
     * 쇼케이스의 댓글 목록을 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param pageToken  페이지 토큰
     * @param size       페이지 크기
     * @return 댓글 페이징 결과
     */
    PageInfo<CommentResult> list(Long showcaseId, String pageToken, int size);
}
