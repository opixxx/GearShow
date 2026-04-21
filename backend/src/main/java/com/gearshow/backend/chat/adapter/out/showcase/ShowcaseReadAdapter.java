package com.gearshow.backend.chat.adapter.out.showcase;

import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.application.port.out.ShowcaseReadPort;
import com.gearshow.backend.chat.domain.exception.ChatRoomShowcaseNotAvailableException;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult.ImageResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseSummaryResult;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseSummariesUseCase;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseUseCase;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chat → showcase 읽기 어댑터.
 *
 * <p>chat 도메인은 showcase 도메인 타입을 직접 소비하지 않고,
 * showcase BC 의 공개 유스케이스 ({@link GetShowcaseUseCase}, {@link GetShowcaseSummariesUseCase})
 * 를 경유해 {@link ShowcaseSummary} 로 변환한다.</p>
 *
 * <p>배치 조회({@link #getSummaries}) 는 {@link GetShowcaseSummariesUseCase} 를 사용해
 * showcase · image 쿼리 각 1회로 해결하여 N+1 을 제거한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ShowcaseReadAdapter implements ShowcaseReadPort {

    private final GetShowcaseUseCase getShowcaseUseCase;
    private final GetShowcaseSummariesUseCase getShowcaseSummariesUseCase;

    @Override
    public ShowcaseSummary getSummary(Long showcaseId) {
        try {
            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(showcaseId);
            return toSummary(detail);
        } catch (NotFoundShowcaseException e) {
            throw new ChatRoomShowcaseNotAvailableException();
        }
    }

    @Override
    public Map<Long, ShowcaseSummary> getSummaries(List<Long> showcaseIds) {
        if (showcaseIds == null || showcaseIds.isEmpty()) {
            return Map.of();
        }
        List<ShowcaseSummaryResult> summaries =
                getShowcaseSummariesUseCase.getSummaries(showcaseIds);

        Map<Long, ShowcaseSummary> result = new HashMap<>();
        for (ShowcaseSummaryResult summary : summaries) {
            result.put(summary.showcaseId(), toSummary(summary));
        }
        return result;
    }

    private ShowcaseSummary toSummary(ShowcaseDetailResult detail) {
        return new ShowcaseSummary(
                detail.showcaseId(),
                detail.ownerId(),
                detail.title(),
                thumbnailOf(detail),
                detail.showcaseStatus() == ShowcaseStatus.ACTIVE);
    }

    private ShowcaseSummary toSummary(ShowcaseSummaryResult summary) {
        return new ShowcaseSummary(
                summary.showcaseId(),
                summary.ownerId(),
                summary.title(),
                summary.primaryImageUrl(),
                summary.showcaseStatus() == ShowcaseStatus.ACTIVE);
    }

    private String thumbnailOf(ShowcaseDetailResult detail) {
        if (detail.images() == null || detail.images().isEmpty()) {
            return null;
        }
        return detail.images().stream()
                .filter(ImageResult::isPrimary)
                .findFirst()
                .or(() -> detail.images().stream().min(Comparator.comparingInt(ImageResult::sortOrder)))
                .map(ImageResult::imageUrl)
                .orElse(null);
    }
}
