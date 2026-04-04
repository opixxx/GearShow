package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ShowcaseBootsSpec;

import java.util.Optional;

/**
 * 쇼케이스 축구화 스펙 Outbound Port.
 */
public interface ShowcaseBootsSpecPort {

    /**
     * 쇼케이스 축구화 스펙을 저장한다.
     */
    ShowcaseBootsSpec save(ShowcaseBootsSpec spec);

    /**
     * 쇼케이스 ID로 축구화 스펙을 조회한다.
     */
    Optional<ShowcaseBootsSpec> findByShowcaseId(Long showcaseId);
}
