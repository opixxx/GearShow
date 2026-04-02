package com.gearshow.backend.showcase.application.port.in;

/**
 * 댓글 삭제 유스케이스.
 */
public interface DeleteCommentUseCase {

    /**
     * 댓글을 삭제한다 (소프트 삭제).
     *
     * @param commentId 댓글 ID
     * @param authorId  요청자 ID (작성자 검증용)
     */
    void delete(Long commentId, Long authorId);
}
