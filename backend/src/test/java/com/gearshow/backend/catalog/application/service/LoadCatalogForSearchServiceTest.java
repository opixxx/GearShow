package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.port.out.BootsSpecPort;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.application.port.out.UniformSpecPort;
import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-018 §D2 + database-optimizer M1: LoadCatalogForSearchService 단위 테스트.
 *
 * <p>5분기 회귀 보호 — catalogItemId null / catalog 미존재 / BOOTS / UNIFORM /
 * BOOTS+spec 없음 / UNIFORM+spec 없음. BOOTS/UNIFORM 분기로 무관 spec 조회가 수행되지
 * 않는 것도 verify.</p>
 */
@ExtendWith(MockitoExtension.class)
class LoadCatalogForSearchServiceTest {

    @Mock
    private CatalogItemPort catalogItemPort;

    @Mock
    private BootsSpecPort bootsSpecPort;

    @Mock
    private UniformSpecPort uniformSpecPort;

    @InjectMocks
    private LoadCatalogForSearchService service;

    @Test
    @DisplayName("catalogItemId 가 null 이면 즉시 empty Optional — port 호출 0")
    void findCatalogSearchSource_nullId_returnsEmpty() {
        Optional<CatalogSearchSource> result = service.findCatalogSearchSource(null);

        assertThat(result).isEmpty();
        verify(catalogItemPort, never()).findById(anyLong());
        verify(bootsSpecPort, never()).findByCatalogItemId(anyLong());
        verify(uniformSpecPort, never()).findByCatalogItemId(anyLong());
    }

    @Test
    @DisplayName("catalogItem 자체가 없으면 empty Optional — spec port 호출 0")
    void findCatalogSearchSource_catalogItemNotFound_returnsEmpty() {
        when(catalogItemPort.findById(999L)).thenReturn(Optional.empty());

        Optional<CatalogSearchSource> result = service.findCatalogSearchSource(999L);

        assertThat(result).isEmpty();
        verify(bootsSpecPort, never()).findByCatalogItemId(anyLong());
        verify(uniformSpecPort, never()).findByCatalogItemId(anyLong());
    }

    @Test
    @DisplayName("BOOTS 카탈로그 — bootsSpecPort 만 호출, uniformSpecPort 미호출")
    void findCatalogSearchSource_boots_callsBootsSpecOnly() {
        CatalogItem item = bootsCatalogItem();
        BootsSpec spec = BootsSpec.builder()
                .id(10L).catalogItemId(1L)
                .studType(StudType.MG)
                .siloName("Mercurial Superfly").siloNameKo("머큐리얼 슈퍼플라이")
                .build();
        when(catalogItemPort.findById(1L)).thenReturn(Optional.of(item));
        when(bootsSpecPort.findByCatalogItemId(1L)).thenReturn(Optional.of(spec));

        Optional<CatalogSearchSource> result = service.findCatalogSearchSource(1L);

        assertThat(result).isPresent();
        assertThat(result.get().siloNameKo()).isEqualTo("머큐리얼 슈퍼플라이");
        assertThat(result.get().clubNameKo()).isNull();
        assertThat(result.get().fullNameKo()).isEqualTo("나이키 머큐리얼 슈퍼플라이");
        assertThat(result.get().fullNameEn()).isEqualTo("Nike Mercurial Superfly");
        assertThat(result.get().brand()).isEqualTo("Nike");
        // ADR-018 §D2 / database-optimizer M1: BOOTS 분기에서 uniformSpec 조회 회피
        verify(uniformSpecPort, never()).findByCatalogItemId(anyLong());
    }

    @Test
    @DisplayName("BOOTS 카탈로그인데 BootsSpec 가 없으면 siloNameKo=null")
    void findCatalogSearchSource_bootsWithoutSpec_returnsNullSilo() {
        when(catalogItemPort.findById(1L)).thenReturn(Optional.of(bootsCatalogItem()));
        when(bootsSpecPort.findByCatalogItemId(1L)).thenReturn(Optional.empty());

        Optional<CatalogSearchSource> result = service.findCatalogSearchSource(1L);

        assertThat(result).isPresent();
        assertThat(result.get().siloNameKo()).isNull();
        assertThat(result.get().clubNameKo()).isNull();
        verify(uniformSpecPort, never()).findByCatalogItemId(anyLong());
    }

    @Test
    @DisplayName("UNIFORM 카탈로그 — uniformSpecPort 만 호출, bootsSpecPort 미호출")
    void findCatalogSearchSource_uniform_callsUniformSpecOnly() {
        CatalogItem item = uniformCatalogItem();
        UniformSpec spec = UniformSpec.builder()
                .id(20L).catalogItemId(2L)
                .clubName("Manchester United").clubNameKo("맨체스터 유나이티드")
                .season("24/25").league("EPL")
                .build();
        when(catalogItemPort.findById(2L)).thenReturn(Optional.of(item));
        when(uniformSpecPort.findByCatalogItemId(2L)).thenReturn(Optional.of(spec));

        Optional<CatalogSearchSource> result = service.findCatalogSearchSource(2L);

        assertThat(result).isPresent();
        assertThat(result.get().clubNameKo()).isEqualTo("맨체스터 유나이티드");
        assertThat(result.get().siloNameKo()).isNull();
        assertThat(result.get().fullNameKo()).isEqualTo("아디다스 맨체스터 유나이티드 24/25");
        // ADR-018 §D2 / database-optimizer M1: UNIFORM 분기에서 bootsSpec 조회 회피
        verify(bootsSpecPort, never()).findByCatalogItemId(anyLong());
    }

    @Test
    @DisplayName("UNIFORM 카탈로그인데 UniformSpec 가 없으면 clubNameKo=null")
    void findCatalogSearchSource_uniformWithoutSpec_returnsNullClub() {
        when(catalogItemPort.findById(2L)).thenReturn(Optional.of(uniformCatalogItem()));
        when(uniformSpecPort.findByCatalogItemId(2L)).thenReturn(Optional.empty());

        Optional<CatalogSearchSource> result = service.findCatalogSearchSource(2L);

        assertThat(result).isPresent();
        assertThat(result.get().clubNameKo()).isNull();
        assertThat(result.get().siloNameKo()).isNull();
        verify(bootsSpecPort, never()).findByCatalogItemId(anyLong());
    }

    private CatalogItem bootsCatalogItem() {
        return CatalogItem.create(
                Category.BOOTS, "Nike", "AT5889-174", null,
                "나이키 머큐리얼 슈퍼플라이", "Nike Mercurial Superfly");
    }

    private CatalogItem uniformCatalogItem() {
        return CatalogItem.create(
                Category.UNIFORM, "Adidas", "MUFC-2425-HOME", null,
                "아디다스 맨체스터 유나이티드 24/25", "Adidas Manchester United 24/25");
    }
}
