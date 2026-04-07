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
     */
    PageInfo<ShowcaseListResult> list(String pageToken, int size);

    /**
     * 내 쇼케이스 목록을 조회한다.
     */
    PageInfo<ShowcaseListResult> listByOwner(Long ownerId, String pageToken, int size,
                                              ShowcaseStatus showcaseStatus);
}
