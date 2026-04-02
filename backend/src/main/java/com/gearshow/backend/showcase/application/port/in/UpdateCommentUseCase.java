package com.gearshow.backend.showcase.application.port.in;

/**
 * 댓글 수정 유스케이스.
 */
public interface UpdateCommentUseCase {

    /**
     * 댓글을 수정한다.
     *
     * @param showcaseId 쇼케이스 ID (소속 검증용)
     * @param commentId  댓글 ID
     * @param authorId   요청자 ID (작성자 검증용)
     * @param content    수정할 내용
     */
    void update(Long showcaseId, Long commentId, Long authorId, String content);
}
