package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;

/**
 * 쇼케이스 경량 요약 결과.
 *
 * <p>목록 페이지 · 외부 컨텍스트(chat 등)가 쇼케이스 제목/소유자/썸네일 정도만 필요로 할 때
 * 사용한다. 상세 조회({@link ShowcaseDetailResult}) 와 달리 image · 3d model · spec 그래프를
 * 로드하지 않아 배치 쿼리로 N+1 을 피할 수 있다.</p>
 *
 * @param showcaseId       쇼케이스 ID
 * @param ownerId          소유자 ID
 * @param title            제목
 * @param primaryImageUrl  대표 이미지 URL (없으면 {@code null})
 * @param showcaseStatus   공개 상태
 */
public record ShowcaseSummaryResult(
        Long showcaseId,
        Long ownerId,
        String title,
        String primaryImageUrl,
        ShowcaseStatus showcaseStatus
) {

    /**
     * 도메인 모델 + 대표 이미지 URL 로부터 요약을 생성한다.
     */
    public static ShowcaseSummaryResult of(Showcase showcase, String primaryImageUrl) {
        return new ShowcaseSummaryResult(
                showcase.getId(),
                showcase.getOwnerId(),
                showcase.getTitle(),
                primaryImageUrl,
                showcase.getStatus()
        );
    }
}
