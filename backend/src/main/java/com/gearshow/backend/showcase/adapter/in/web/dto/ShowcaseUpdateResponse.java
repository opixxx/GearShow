package com.gearshow.backend.showcase.adapter.in.web.dto;

/**
 * 쇼케이스 수정 응답 DTO.
 *
 * <p>클라이언트가 어떤 쇼케이스의 응답인지 추적할 수 있도록 path variable 의 {@code showcaseId} 를
 * 응답 본문에도 echo 한다.</p>
 */
public record ShowcaseUpdateResponse(Long showcaseId) {

    public static ShowcaseUpdateResponse of(Long showcaseId) {
        return new ShowcaseUpdateResponse(showcaseId);
    }
}
