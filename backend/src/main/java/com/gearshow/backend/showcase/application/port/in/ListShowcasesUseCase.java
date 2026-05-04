package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import com.gearshow.backend.common.dto.PageInfo;

/**
 * 쇼케이스 목록 조회 유스케이스.
 */
public interface ListShowcasesUseCase {

    /**
     * 쇼케이스 목록을 조회한다 (최신순, 공개 목록).
     *
     * <p>ADR-019: {@code keyword} 가 non-null/non-blank 이면 {@code search_text} LIKE 매칭
     * (대소문자 무시) 결과만 반환. {@code null} 이면 전체 ACTIVE 목록 (기존 동작).</p>
     *
     * @param keyword   검색어 (nullable, 빈 문자열은 호출자 책임)
     * @param pageToken cursor 페이지 토큰
     * @param size      페이지 크기
     */
    PageInfo<ShowcaseListResult> list(String keyword, String pageToken, int size);

    /**
     * 내 쇼케이스 목록을 조회한다.
     */
    PageInfo<ShowcaseListResult> listByOwner(Long ownerId, String pageToken, int size,
                                              ShowcaseStatus showcaseStatus);
}
