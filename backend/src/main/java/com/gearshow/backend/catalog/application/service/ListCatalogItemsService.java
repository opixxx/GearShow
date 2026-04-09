package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CatalogItemListResult;
import com.gearshow.backend.catalog.application.port.in.ListCatalogItemsUseCase;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.util.PageTokenUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    public PageInfo<CatalogItemListResult> list(String pageToken, int size) {
        List<CatalogItem> items;
        if (pageToken == null) {
            items = catalogItemPort.findAllFirstPage(size);
        } else {
            Pair<Instant, Long> cursor = PageTokenUtil.decode(pageToken, Instant.class, Long.class);
            items = catalogItemPort.findAllWithCursor(cursor.getLeft(), cursor.getRight(), size);
        }

        List<CatalogItemListResult> results = items.stream()
                .map(CatalogItemListResult::from)
                .toList();

        return PageInfo.of(results, size, CatalogItemListResult::createdAt, CatalogItemListResult::catalogItemId);
    }
}
