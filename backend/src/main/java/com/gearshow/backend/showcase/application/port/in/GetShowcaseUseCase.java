package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;

/**
 * 쇼케이스 상세 조회 유스케이스.
 */
public interface GetShowcaseUseCase {

    /**
     * 쇼케이스 상세를 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 상세 조회 결과
     */
    ShowcaseDetailResult getShowcase(Long showcaseId);
}
