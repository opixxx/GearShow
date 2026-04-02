package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.port.in.ListShowcasesUseCase;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 쇼케이스 목록 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class ListShowcasesService implements ListShowcasesUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final ShowcaseCommentPort showcaseCommentPort;
    private final Showcase3dModelPort showcase3dModelPort;
    private final CatalogItemPort catalogItemPort;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<ShowcaseListResult> list(String pageToken, int size,
                                              Category category, String brand, String keyword,
                                              Boolean isForSale, ConditionGrade conditionGrade) {
        List<Long> catalogItemIds = findCatalogItemIds(category, brand);

        List<Showcase> showcases;
        if (pageToken == null) {
            showcases = showcasePort.findAllFirstPage(
                    size, catalogItemIds, keyword, isForSale, conditionGrade);
        } else {
            Pair<Instant, Long> cursor = PageTokenUtil.decode(pageToken, Instant.class, Long.class);
            showcases = showcasePort.findAllWithCursor(
                    cursor.getLeft(), cursor.getRight(), size,
                    catalogItemIds, keyword, isForSale, conditionGrade);
        }

        return toPageInfo(showcases, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageInfo<ShowcaseListResult> listByOwner(Long ownerId, String pageToken, int size,
                                                     ShowcaseStatus showcaseStatus) {
        List<Showcase> showcases;
        if (pageToken == null) {
            showcases = showcasePort.findByOwnerIdFirstPage(ownerId, size, showcaseStatus);
        } else {
            Pair<Instant, Long> cursor = PageTokenUtil.decode(pageToken, Instant.class, Long.class);
            showcases = showcasePort.findByOwnerIdWithCursor(
                    ownerId, cursor.getLeft(), cursor.getRight(), size, showcaseStatus);
        }

        return toPageInfo(showcases, size);
    }

    /**
     * category/brand 필터가 있으면 해당하는 카탈로그 아이템 ID 목록을 조회한다.
     * 필터가 없으면 null을 반환하여 전체 조회되도록 한다.
     */
    private List<Long> findCatalogItemIds(Category category, String brand) {
        if (category == null && brand == null) {
            return null;
        }
        return catalogItemPort.findIdsByCategoryAndBrand(category, brand);
    }

    /**
     * 쇼케이스 목록을 PageInfo로 변환한다.
     * 대표 이미지, 댓글 수, 3D 모델 존재 여부를 IN절 배치 조회로 가져온다.
     */
    private PageInfo<ShowcaseListResult> toPageInfo(List<Showcase> showcases, int size) {
        if (showcases.isEmpty()) {
            return PageInfo.of(List.of(), size,
                    ShowcaseListResult::createdAt,
                    ShowcaseListResult::showcaseId);
        }

        List<Long> showcaseIds = showcases.stream()
                .map(Showcase::getId)
                .toList();

        // 3건의 배치 쿼리로 부가 정보 일괄 조회
        Map<Long, String> primaryImageMap = showcaseImagePort.findPrimaryImageUrlsByShowcaseIds(showcaseIds);
        Map<Long, Integer> commentCountMap = showcaseCommentPort.countActiveByShowcaseIds(showcaseIds);
        Set<Long> showcaseIdsWithModel = showcase3dModelPort.findShowcaseIdsWithModel(showcaseIds);

        List<ShowcaseListResult> results = showcases.stream()
                .map(showcase -> new ShowcaseListResult(
                        showcase.getId(),
                        showcase.getTitle(),
                        showcase.getConditionGrade(),
                        showcase.isForSale(),
                        showcase.getWearCount(),
                        primaryImageMap.getOrDefault(showcase.getId(), null),
                        commentCountMap.getOrDefault(showcase.getId(), 0),
                        showcaseIdsWithModel.contains(showcase.getId()),
                        showcase.getCreatedAt()))
                .toList();

        return PageInfo.of(results, size,
                ShowcaseListResult::createdAt,
                ShowcaseListResult::showcaseId);
    }
}
