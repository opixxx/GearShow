package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
    public List<CatalogItem> findAllFirstPage(Category category, String keyword, int size) {
        return catalogItemJpaRepository.findAllFirstPage(
                        CatalogStatus.ACTIVE, category, toLikePattern(keyword), PageRequest.of(0, size + 1))
                .stream()
                .map(catalogItemMapper::toDomain)
                .toList();
    }

    @Override
    public List<CatalogItem> findAllWithCursor(Category category, String keyword,
                                               Instant cursorCreatedAt, Long cursorId, int size) {
        return catalogItemJpaRepository.findAllWithCursor(
                        CatalogStatus.ACTIVE, category, toLikePattern(keyword),
                        cursorCreatedAt, cursorId, PageRequest.of(0, size + 1))
                .stream()
                .map(catalogItemMapper::toDomain)
                .toList();
    }

    /**
     * 사용자 키워드를 LIKE 패턴으로 변환한다.
     * 공백 trim 후 비어 있으면 null (필터 미적용).
     * LIKE 메타문자({@code %}, {@code _}, {@code \})는 리터럴로 escape하여 의도 외 매칭과
     * {@code ?keyword=%} 형태의 amplification을 차단한다 (ADR-019 §D1 정책 일관화).
     * JPQL의 {@code ESCAPE '\\'}와 짝을 이뤄 동작.
     */
    private String toLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String escaped = trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
