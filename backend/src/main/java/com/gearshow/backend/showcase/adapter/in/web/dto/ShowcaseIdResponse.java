package com.gearshow.backend.showcase.adapter.in.web.dto;

/**
 * 쇼케이스 ID만 반환하는 응답 DTO.
 * 수정 등 ID 확인이 필요한 단순 응답에 사용된다.
 *
 * @param showcaseId 쇼케이스 ID
 */
public record ShowcaseIdResponse(Long showcaseId) {
}
