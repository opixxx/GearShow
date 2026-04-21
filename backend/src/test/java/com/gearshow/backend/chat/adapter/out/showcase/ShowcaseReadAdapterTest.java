package com.gearshow.backend.chat.adapter.out.showcase;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.chat.application.dto.ShowcaseSummary;
import com.gearshow.backend.chat.domain.exception.ChatRoomShowcaseNotAvailableException;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult.ImageResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseSummaryResult;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseSummariesUseCase;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseUseCase;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ShowcaseReadAdapterTest {

    @InjectMocks
    private ShowcaseReadAdapter adapter;

    @Mock private GetShowcaseUseCase getShowcaseUseCase;
    @Mock private GetShowcaseSummariesUseCase getShowcaseSummariesUseCase;

    private ShowcaseDetailResult detail(Long id, ShowcaseStatus status,
                                        List<ImageResult> images) {
        return new ShowcaseDetailResult(
                id, 10L, null, Category.BOOTS, "Nike", "DJ", "title", "desc",
                "270", ConditionGrade.A, 0, false, status,
                images, null, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("ACTIVE + isPrimary 이미지 있으면 chatStartable=true, 해당 url을 thumbnail로")
    void getSummary_activeWithPrimary() {
        given(getShowcaseUseCase.getShowcase(1L)).willReturn(detail(1L, ShowcaseStatus.ACTIVE,
                List.of(new ImageResult(1L, "primary.jpg", 0, true),
                        new ImageResult(2L, "second.jpg", 1, false))));

        ShowcaseSummary s = adapter.getSummary(1L);

        assertThat(s.chatStartable()).isTrue();
        assertThat(s.thumbnailUrl()).isEqualTo("primary.jpg");
        assertThat(s.sellerId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("isPrimary 없으면 sortOrder 최솟값 이미지로 폴백")
    void getSummary_noPrimary_fallbackToFirstSorted() {
        given(getShowcaseUseCase.getShowcase(1L)).willReturn(detail(1L, ShowcaseStatus.ACTIVE,
                List.of(new ImageResult(1L, "b.jpg", 5, false),
                        new ImageResult(2L, "a.jpg", 0, false))));

        ShowcaseSummary s = adapter.getSummary(1L);

        assertThat(s.thumbnailUrl()).isEqualTo("a.jpg");
    }

    @Test
    @DisplayName("이미지가 비어 있으면 thumbnail null")
    void getSummary_noImages_thumbnailNull() {
        given(getShowcaseUseCase.getShowcase(1L)).willReturn(detail(1L, ShowcaseStatus.ACTIVE, List.of()));

        ShowcaseSummary s = adapter.getSummary(1L);

        assertThat(s.thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("SOLD 상태 쇼케이스는 chatStartable=false")
    void getSummary_sold_chatStartableFalse() {
        given(getShowcaseUseCase.getShowcase(1L)).willReturn(detail(1L, ShowcaseStatus.SOLD,
                List.of(new ImageResult(1L, "p.jpg", 0, true))));

        ShowcaseSummary s = adapter.getSummary(1L);

        assertThat(s.chatStartable()).isFalse();
    }

    @Test
    @DisplayName("쇼케이스 NotFound는 ChatRoomShowcaseNotAvailableException으로 변환")
    void getSummary_notFound_throws() {
        given(getShowcaseUseCase.getShowcase(99L)).willThrow(new NotFoundShowcaseException());

        assertThatThrownBy(() -> adapter.getSummary(99L))
                .isInstanceOf(ChatRoomShowcaseNotAvailableException.class);
    }

    @Test
    @DisplayName("getSummaries는 배치 UseCase 결과를 매핑하며 존재하지 않는 ID는 누락된다")
    void getSummaries_skipsNotFound() {
        // given: batch UseCase가 1번만 반환 (2번은 존재하지 않아 누락)
        given(getShowcaseSummariesUseCase.getSummaries(List.of(1L, 2L)))
                .willReturn(List.of(new ShowcaseSummaryResult(
                        1L, 10L, "title", "p.jpg", ShowcaseStatus.ACTIVE)));

        // when
        Map<Long, ShowcaseSummary> result = adapter.getSummaries(List.of(1L, 2L));

        // then
        assertThat(result).containsKey(1L).doesNotContainKey(2L);
        assertThat(result.get(1L).chatStartable()).isTrue();
        assertThat(result.get(1L).thumbnailUrl()).isEqualTo("p.jpg");
    }

    @Test
    @DisplayName("getSummaries는 SOLD 상태를 chatStartable=false 로 매핑한다")
    void getSummaries_soldIsNotStartable() {
        // given
        given(getShowcaseSummariesUseCase.getSummaries(List.of(1L)))
                .willReturn(List.of(new ShowcaseSummaryResult(
                        1L, 10L, "title", null, ShowcaseStatus.SOLD)));

        // when
        Map<Long, ShowcaseSummary> result = adapter.getSummaries(List.of(1L));

        // then
        assertThat(result.get(1L).chatStartable()).isFalse();
    }
}
