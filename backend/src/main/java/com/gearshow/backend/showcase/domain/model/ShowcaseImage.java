package com.gearshow.backend.showcase.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 쇼케이스 이미지 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 쇼케이스당 최소 1개 이상 존재해야 한다.</p>
 */
@Getter
public class ShowcaseImage {

    private final Long id;
    private final Long showcaseId;
    private final String imageUrl;
    private final int sortOrder;
    private final boolean primary;
    private final LocalDateTime createdAt;

    @Builder
    private ShowcaseImage(Long id, Long showcaseId, String imageUrl,
                          int sortOrder, boolean primary, LocalDateTime createdAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.primary = primary;
        this.createdAt = createdAt;
    }

    /**
     * 새로운 쇼케이스 이미지를 생성한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param imageUrl   이미지 URL
     * @param sortOrder  정렬 순서
     * @param primary    대표 이미지 여부
     * @return 생성된 쇼케이스 이미지
     */
    public static ShowcaseImage create(Long showcaseId, String imageUrl,
                                       int sortOrder, boolean primary) {
        return ShowcaseImage.builder()
                .showcaseId(showcaseId)
                .imageUrl(imageUrl)
                .sortOrder(sortOrder)
                .primary(primary)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
