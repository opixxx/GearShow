package com.gearshow.backend.chat.adapter.out.showcase;

import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.application.port.out.ShowcaseReadPort;
import com.gearshow.backend.chat.domain.exception.ChatRoomShowcaseNotAvailableException;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult.ImageResult;
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
 * <p>showcase BC의 공개 유스케이스({@link GetShowcaseUseCase})를 경유해
 * {@link ShowcaseSummary}로 변환한다. chat 도메인은 showcase 도메인 타입을 직접 소비하지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class ShowcaseReadAdapter implements ShowcaseReadPort {

    private final GetShowcaseUseCase getShowcaseUseCase;

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
        Map<Long, ShowcaseSummary> result = new HashMap<>();
        for (Long id : showcaseIds) {
            try {
                ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(id);
                result.put(id, toSummary(detail));
            } catch (NotFoundShowcaseException e) {
                // 목록 조회 중 개별 쇼케이스가 사라진 경우 해당 항목만 누락 처리
            }
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
