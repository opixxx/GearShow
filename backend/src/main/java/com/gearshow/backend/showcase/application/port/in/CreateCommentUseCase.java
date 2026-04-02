package com.gearshow.backend.showcase.application.port.in;

/**
 * 댓글 작성 유스케이스.
 */
public interface CreateCommentUseCase {

    /**
     * 댓글을 작성한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param authorId   작성자 ID
     * @param content    댓글 내용
     * @return 생성된 댓글 ID
     */
    Long create(Long showcaseId, Long authorId, String content);
}
