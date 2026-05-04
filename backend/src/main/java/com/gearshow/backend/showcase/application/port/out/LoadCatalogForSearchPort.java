package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;

import java.util.Optional;

/**
 * 검색 텍스트 합성용 catalog read 포트 (ADR-018).
 *
 * <p>showcase BC 가 catalog BC 의 도메인 객체를 직접 import 하지 않고도 한국어 alias 를
 * 가져오기 위한 outbound 포트. 구현체는 catalog BC 측에 위치하며 BOOTS / UNIFORM 분기를
 * catalog 가 책임지고 한국어 alias 를 평탄화한 record 로 노출한다.</p>
 */
public interface LoadCatalogForSearchPort {

    /**
     * 카탈로그 아이템의 검색 텍스트 합성 source 를 조회한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @return 한국어/영문 풀네임 + 한국어 alias 가 평탄화된 read model. 카탈로그 미존재 시 empty.
     */
    Optional<CatalogSearchSource> findCatalogSearchSource(Long catalogItemId);
}
