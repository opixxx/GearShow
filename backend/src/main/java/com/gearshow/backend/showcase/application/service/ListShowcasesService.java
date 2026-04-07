package com.gearshow.backend.showcase.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult.BootsSpecSummary;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult.SpecSummary;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult.UniformSpecSummary;
import com.gearshow.backend.showcase.application.port.in.ListShowcasesUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseSpecPort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 쇼케이스 목록 조회 유스케이스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListShowcasesService implements ListShowcasesUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseCommentPort showcaseCommentPort;
    private final ShowcaseSpecPort showcaseSpecPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<ShowcaseListResult> list(String pageToken, int size) {
        List<Showcase> showcases;
        if (pageToken == null) {
            showcases = showcasePort.findAllFirstPage(size);
        } else {
            Pair<Instant, Long> cursor = PageTokenUtil.decode(pageToken, Instant.class, Long.class);
            showcases = showcasePort.findAllWithCursor(cursor.getLeft(), cursor.getRight(), size);
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
     * 쇼케이스 목록을 PageInfo로 변환한다.
     * primaryImageUrl, has3dModel은 Showcase에 비정규화되어 있으므로 추가 쿼리 불필요.
     * 댓글 수, 스펙 정보만 IN절 배치 조회로 가져온다.
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

        // 2건의 배치 쿼리로 부가 정보 일괄 조회
        Map<Long, Integer> commentCountMap = showcaseCommentPort.countActiveByShowcaseIds(showcaseIds);
        Map<Long, ShowcaseSpec> specMap = showcaseSpecPort.findByShowcaseIds(showcaseIds);

        List<ShowcaseListResult> results = showcases.stream()
                .map(showcase -> new ShowcaseListResult(
                        showcase.getId(),
                        showcase.getTitle(),
                        showcase.getCategory(),
                        showcase.getBrand(),
                        showcase.getUserSize(),
                        showcase.getConditionGrade(),
                        showcase.isForSale(),
                        showcase.getWearCount(),
                        showcase.getPrimaryImageUrl(),
                        commentCountMap.getOrDefault(showcase.getId(), 0),
                        showcase.isHas3dModel(),
                        toSpecSummary(specMap.get(showcase.getId())),
                        showcase.getCreatedAt()))
                .toList();

        return PageInfo.of(results, size,
                ShowcaseListResult::createdAt,
                ShowcaseListResult::showcaseId);
    }

    /**
     * ShowcaseSpec의 specType에 따라 JSON을 적절한 SpecSummary로 변환한다.
     */
    private SpecSummary toSpecSummary(ShowcaseSpec spec) {
        if (spec == null) {
            return null;
        }
        try {
            return switch (spec.getSpecType()) {
                case BOOTS -> objectMapper.readValue(spec.getSpecData(), BootsSpecSummary.class);
                case UNIFORM -> objectMapper.readValue(spec.getSpecData(), UniformSpecSummary.class);
            };
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("스펙 JSON 파싱 실패 - showcaseId: {}, specType: {}",
                    spec.getShowcaseId(), spec.getSpecType(), e);
            return null;
        }
    }
}
