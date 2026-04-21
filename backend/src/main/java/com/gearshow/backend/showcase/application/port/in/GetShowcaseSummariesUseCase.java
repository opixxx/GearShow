package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.ShowcaseSummaryResult;

import java.util.Collection;
import java.util.List;

/**
 * 여러 쇼케이스의 경량 요약을 한 번에 조회하는 유스케이스.
 *
 * <p>N+1 을 피하기 위해 chat 등 외부 컨텍스트가 배치 조회에 사용한다.
 * 상세 정보가 필요하면 {@link GetShowcaseUseCase} 를 사용한다.</p>
 */
public interface GetShowcaseSummariesUseCase {

    /**
     * 복수 쇼케이스의 경량 요약을 조회한다. 존재하지 않는 ID 는 결과에서 누락된다.
     *
     * @param showcaseIds 쇼케이스 ID 컬렉션. 빈 컬렉션이면 빈 리스트 반환.
     * @return 조회된 요약 목록 (순서 보장 없음)
     */
    List<ShowcaseSummaryResult> getSummaries(Collection<Long> showcaseIds);
}
