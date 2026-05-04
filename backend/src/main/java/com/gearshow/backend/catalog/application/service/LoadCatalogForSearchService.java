package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.port.out.BootsSpecPort;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.application.port.out.UniformSpecPort;
import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;
import com.gearshow.backend.showcase.application.port.out.LoadCatalogForSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Showcase 검색 텍스트 합성 source 를 catalog 측에서 조회하여 평탄화하는 어댑터 (ADR-018 §D2).
 *
 * <p>showcase BC 의 {@link LoadCatalogForSearchPort} 를 catalog 측에서 구현. catalog 의
 * {@link CatalogItem} + {@link BootsSpec} + {@link UniformSpec} 을 read 후 한국어 alias 만
 * 평탄화한 record 로 노출. BOOTS / UNIFORM 분기는 본 클래스가 책임지고, showcase 는
 * record 를 그대로 사용한다.</p>
 *
 * <p><b>의존 방향</b>: catalog → showcase application(port + dto). showcase 는 catalog 도메인을
 * 직접 import 하지 않으므로 BC 격리 유지.</p>
 */
@Service
@RequiredArgsConstructor
public class LoadCatalogForSearchService implements LoadCatalogForSearchPort {

    private final CatalogItemPort catalogItemPort;
    private final BootsSpecPort bootsSpecPort;
    private final UniformSpecPort uniformSpecPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogSearchSource> findCatalogSearchSource(Long catalogItemId) {
        if (catalogItemId == null) {
            return Optional.empty();
        }
        return catalogItemPort.findById(catalogItemId)
                .map(item -> toSearchSource(item, catalogItemId));
    }

    private CatalogSearchSource toSearchSource(CatalogItem item, Long catalogItemId) {
        // ADR-018 §D2 + database-optimizer M1: BOOTS/UNIFORM 분기로 무관 spec 조회 회피.
        // CatalogItem 의 category 가 BOOTS 면 uniform_spec 조회는 항상 결과 없음 (UNIQUE 제약상 0행),
        // UNIFORM 도 마찬가지이므로 단일 spec read 만 수행한다.
        String siloNameKo = null;
        String clubNameKo = null;
        Category category = item.getCategory();
        if (category == Category.BOOTS) {
            siloNameKo = bootsSpecPort.findByCatalogItemId(catalogItemId)
                    .map(BootsSpec::getSiloNameKo)
                    .orElse(null);
        } else if (category == Category.UNIFORM) {
            clubNameKo = uniformSpecPort.findByCatalogItemId(catalogItemId)
                    .map(UniformSpec::getClubNameKo)
                    .orElse(null);
        }
        return new CatalogSearchSource(
                item.getFullNameKo(),
                item.getFullNameEn(),
                item.getBrand(),
                siloNameKo,
                clubNameKo
        );
    }
}
