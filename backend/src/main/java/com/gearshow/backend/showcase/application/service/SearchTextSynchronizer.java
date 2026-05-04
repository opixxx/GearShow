package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;
import com.gearshow.backend.showcase.application.port.out.LoadCatalogForSearchPort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Showcase 의 search_text 합성 + 도메인 객체에 주입을 담당하는 application 컴포넌트 (ADR-018).
 *
 * <p>등록·수정 흐름의 search_text 갱신 로직을 단일 진입점으로 모은다. {@link CreateShowcaseService}
 * 와 {@link UpdateShowcaseService} 가 동일한 {@code (port 조회 → compose → changeSearchText)} 패턴을
 * 중복으로 갖고 있던 것을 본 컴포넌트로 추출.</p>
 *
 * <p>합성 자체는 순수 함수 {@link SearchTextComposer#compose}, 본 컴포넌트는 catalog read 트리거
 * + 도메인 객체에 결과 주입까지 책임진다.</p>
 */
@Component
@RequiredArgsConstructor
public class SearchTextSynchronizer {

    private final LoadCatalogForSearchPort loadCatalogForSearchPort;

    /**
     * Showcase 의 search_text 를 catalog 한국어 alias + 직접 입력값으로 재합성한 새 인스턴스를 반환.
     *
     * <p>catalogItemId 가 null 이면 catalog 조회를 생략하고 직접 입력값만으로 합성.
     * catalogItemId 가 있어도 catalog 가 존재하지 않으면 source=null 로 처리 (조용히 fallback).</p>
     */
    public Showcase synchronize(Showcase showcase) {
        CatalogSearchSource source = showcase.getCatalogItemId() != null
                ? loadCatalogForSearchPort.findCatalogSearchSource(showcase.getCatalogItemId()).orElse(null)
                : null;
        return showcase.changeSearchText(SearchTextComposer.compose(showcase, source));
    }
}
