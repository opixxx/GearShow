package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.ShowcaseSummaryResult;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetShowcaseSummariesServiceTest {

    @InjectMocks
    private GetShowcaseSummariesService service;

    @Mock
    private ShowcasePort showcasePort;

    @Mock
    private ShowcaseImagePort showcaseImagePort;

    private Showcase showcase(Long id, Long ownerId, String title, ShowcaseStatus status) {
        Instant fixed = Instant.parse("2026-04-22T00:00:00Z");
        return Showcase.builder()
                .id(id)
                .ownerId(ownerId)
                .category(Category.BOOTS)
                .brand("Nike")
                .modelCode("DJ-" + id)
                .title(title)
                .description("desc")
                .userSize("270")
                .conditionGrade(ConditionGrade.A)
                .wearCount(0)
                .forSale(false)
                .primaryImageUrl(null)
                .has3dModel(false)
                .status(status)
                .createdAt(fixed)
                .updatedAt(fixed)
                .build();
    }

    @Test
    @DisplayName("빈 컬렉션을 넘기면 빈 리스트를 반환하고 포트를 호출하지 않는다")
    void getSummaries_emptyIds_shortCircuits() {
        // when
        List<ShowcaseSummaryResult> result = service.getSummaries(List.of());

        // then
        assertThat(result).isEmpty();
        verify(showcasePort, never()).findAllByIds(org.mockito.ArgumentMatchers.any());
        verify(showcaseImagePort, never())
                .findPrimaryImageUrlsByShowcaseIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("null 컬렉션을 넘겨도 안전하게 빈 리스트를 반환한다")
    void getSummaries_nullIds_returnsEmpty() {
        // when
        List<ShowcaseSummaryResult> result = service.getSummaries(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("여러 ID 를 1회의 showcase + 1회의 image 쿼리로 조립한다 (N+1 제거)")
    void getSummaries_mapsEachFoundIdWithPrimaryImage() {
        // given
        List<Long> ids = List.of(1L, 2L, 3L);
        given(showcasePort.findAllByIds(ids)).willReturn(List.of(
                showcase(1L, 10L, "부츠 A", ShowcaseStatus.ACTIVE),
                showcase(2L, 20L, "부츠 B", ShowcaseStatus.SOLD),
                showcase(3L, 30L, "부츠 C", ShowcaseStatus.HIDDEN)));
        given(showcaseImagePort.findPrimaryImageUrlsByShowcaseIds(List.of(1L, 2L, 3L)))
                .willReturn(Map.of(
                        1L, "https://cdn/a.jpg",
                        2L, "https://cdn/b.jpg"
                        // 3L 은 이미지 없음
                ));

        // when
        List<ShowcaseSummaryResult> result = service.getSummaries(ids);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ShowcaseSummaryResult::showcaseId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);

        ShowcaseSummaryResult a = result.stream()
                .filter(r -> r.showcaseId().equals(1L)).findFirst().orElseThrow();
        assertThat(a.ownerId()).isEqualTo(10L);
        assertThat(a.title()).isEqualTo("부츠 A");
        assertThat(a.primaryImageUrl()).isEqualTo("https://cdn/a.jpg");
        assertThat(a.showcaseStatus()).isEqualTo(ShowcaseStatus.ACTIVE);

        ShowcaseSummaryResult c = result.stream()
                .filter(r -> r.showcaseId().equals(3L)).findFirst().orElseThrow();
        assertThat(c.primaryImageUrl()).isNull(); // 이미지 없는 경우
        assertThat(c.showcaseStatus()).isEqualTo(ShowcaseStatus.HIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 ID 는 결과에서 누락된다")
    void getSummaries_missingIds_areOmitted() {
        // given
        given(showcasePort.findAllByIds(List.of(1L, 999L)))
                .willReturn(List.of(showcase(1L, 10L, "A", ShowcaseStatus.ACTIVE)));
        given(showcaseImagePort.findPrimaryImageUrlsByShowcaseIds(List.of(1L)))
                .willReturn(Map.of());

        // when
        List<ShowcaseSummaryResult> result = service.getSummaries(List.of(1L, 999L));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).showcaseId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("showcase 배치 조회 결과가 비면 image 포트를 호출하지 않는다")
    void getSummaries_noShowcases_skipsImageFetch() {
        // given
        given(showcasePort.findAllByIds(List.of(999L))).willReturn(List.of());

        // when
        List<ShowcaseSummaryResult> result = service.getSummaries(List.of(999L));

        // then
        assertThat(result).isEmpty();
        verify(showcaseImagePort, never())
                .findPrimaryImageUrlsByShowcaseIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("image 포트는 실제로 조회된 showcase id 만으로 호출된다")
    void getSummaries_imagePortCalledWithFoundIdsOnly() {
        // given: 2L 은 존재하지 않음
        given(showcasePort.findAllByIds(List.of(1L, 2L))).willReturn(List.of(
                showcase(1L, 10L, "A", ShowcaseStatus.ACTIVE)));
        given(showcaseImagePort.findPrimaryImageUrlsByShowcaseIds(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(Map.of());

        // when
        service.getSummaries(List.of(1L, 2L));

        // then: image 포트는 존재 확인된 [1L] 만 받아야 함 (없는 2L 로 쿼리 낭비 금지)
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(showcaseImagePort).findPrimaryImageUrlsByShowcaseIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L);
    }
}
