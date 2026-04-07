package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 쇼케이스 스펙 Outbound Port.
 * 모든 카테고리의 스펙을 단일 포트로 관리한다.
 */
public interface ShowcaseSpecPort {

    /**
     * 쇼케이스 스펙을 저장한다.
     */
    ShowcaseSpec save(ShowcaseSpec spec);

    /**
     * 쇼케이스 ID로 스펙을 조회한다.
     */
    Optional<ShowcaseSpec> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID 목록으로 스펙을 일괄 조회한다.
     *
     * @param showcaseIds 쇼케이스 ID 목록
     * @return 쇼케이스 ID → 스펙 맵
     */
    Map<Long, ShowcaseSpec> findByShowcaseIds(List<Long> showcaseIds);
}
