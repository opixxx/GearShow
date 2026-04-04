package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ShowcaseUniformSpec;

import java.util.Optional;

/**
 * 쇼케이스 유니폼 스펙 Outbound Port.
 */
public interface ShowcaseUniformSpecPort {

    /**
     * 쇼케이스 유니폼 스펙을 저장한다.
     */
    ShowcaseUniformSpec save(ShowcaseUniformSpec spec);

    /**
     * 쇼케이스 ID로 유니폼 스펙을 조회한다.
     */
    Optional<ShowcaseUniformSpec> findByShowcaseId(Long showcaseId);
}
