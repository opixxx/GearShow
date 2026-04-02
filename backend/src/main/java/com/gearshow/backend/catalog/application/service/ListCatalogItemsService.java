package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CatalogItemListResult;
import com.gearshow.backend.catalog.application.port.in.ListCatalogItemsUseCase;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 카탈로그 아이템 목록 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class ListCatalogItemsService implements ListCatalogItemsUseCase {

    private final CatalogItemPort catalogItemPort;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<CatalogItemListResult> list(
            String pageToken, int size, Category category, String brand, String keyword) {

        Long cursorId = decodeCursorId(pageToken);

        List<CatalogItem> items = catalogItemPort.findAllWithCursor(
                cursorId, size, category, brand, keyword);

        List<CatalogItemListResult> results = items.stream()
                .map(CatalogItemListResult::from)
                .toList();

        return PageInfo.of(results, size,
                CatalogItemListResult::catalogItemId,
                CatalogItemListResult::catalogItemId);
    }

    /**
     * pageToken에서 커서 ID를 추출한다.
     */
    private Long decodeCursorId(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return null;
        }
        Pair<Long, Long> decoded = PageTokenUtil.decode(pageToken, Long.class, Long.class);
        return decoded.getLeft();
    }
}
