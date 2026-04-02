package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 카탈로그 아이템 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class CatalogItemPersistenceAdapter implements CatalogItemPort {

    private final CatalogItemJpaRepository catalogItemJpaRepository;
    private final CatalogItemMapper catalogItemMapper;

    @Override
    public CatalogItem save(CatalogItem catalogItem) {
        CatalogItemJpaEntity entity = catalogItemMapper.toJpaEntity(catalogItem);
        CatalogItemJpaEntity saved = catalogItemJpaRepository.save(entity);
        return catalogItemMapper.toDomain(saved);
    }

    @Override
    public Optional<CatalogItem> findById(Long id) {
        return catalogItemJpaRepository.findById(id)
                .map(catalogItemMapper::toDomain);
    }

    @Override
    public List<Long> findIdsByCategoryAndBrand(Category category, String brand) {
        return catalogItemJpaRepository.findIdsByCategoryAndBrand(category, brand);
    }

    @Override
    public boolean existsByCategoryAndModelCode(Category category, String modelCode) {
        return catalogItemJpaRepository.existsByCategoryAndModelCode(category, modelCode);
    }

    @Override
    public List<CatalogItem> findAllWithCursor(Long cursorId, int size,
                                               Category category, String brand, String keyword) {
        return catalogItemJpaRepository.findAllWithCursor(
                        CatalogStatus.ACTIVE, cursorId, category, brand, keyword, PageRequest.of(0, size + 1))
                .stream()
                .map(catalogItemMapper::toDomain)
                .toList();
    }
}
