package com.gearshow.backend.showcase.adapter.in.web.dto;

/**
 * 댓글 ID만 반환하는 응답 DTO.
 * 작성·수정 등 ID 확인이 필요한 단순 응답에 사용된다.
 *
 * @param showcaseCommentId 쇼케이스 댓글 ID
 */
public record CommentIdResponse(Long showcaseCommentId) {
}
