package com.gearshow.backend.showcase.application.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gearshow.backend.showcase.application.dto.ShowcaseSummaryResult;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseSummariesUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase;

import lombok.RequiredArgsConstructor;

/**
 * 쇼케이스 경량 요약 배치 조회 유스케이스 구현체.
 *
 * <p>상태 필터링 없이 요청된 모든 쇼케이스를 반환한다 (HIDDEN, DELETED 포함).
 * 호출측이 상태값({@link ShowcaseSummaryResult#showcaseStatus()}) 을 직접 보고 렌더링 여부를 결정한다.
 * 이는 {@link GetShowcaseService} 의 공개 상세 조회(HIDDEN/DELETED 차단) 와 다른 정책이다 —
 * 목록 컨텍스트에서는 "쇼케이스가 삭제되었음" 상태 표시가 필요하기 때문이다.</p>
 */
@Service
@RequiredArgsConstructor
public class GetShowcaseSummariesService implements GetShowcaseSummariesUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;

    @Override
    @Transactional(readOnly = true)
    public List<ShowcaseSummaryResult> getSummaries(Collection<Long> showcaseIds) {
        if (showcaseIds == null || showcaseIds.isEmpty()) {
            return List.of();
        }

        List<Showcase> showcases = showcasePort.findAllByIds(showcaseIds);
        if (showcases.isEmpty()) {
            return List.of();
        }

        List<Long> foundIds = showcases.stream().map(Showcase::getId).toList();
        Map<Long, String> primaryImageUrls =
                showcaseImagePort.findPrimaryImageUrlsByShowcaseIds(foundIds);

        return showcases.stream()
                .map(showcase -> ShowcaseSummaryResult.of(
                        showcase, primaryImageUrls.get(showcase.getId())))
                .toList();
    }
}
