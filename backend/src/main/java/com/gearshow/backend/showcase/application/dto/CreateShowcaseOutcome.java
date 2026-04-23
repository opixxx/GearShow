package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.model.Showcase;

/**
 * {@code CreateShowcaseService.saveShowcaseWithSpec} 의 반환 타입.
 *
 * <p>신규 생성과 content_hash 기반 dedup 히트를 구분하여, 호출자(Facade)가
 * 3D 모델 생성 같은 후속 외부 I/O 를 중복 트리거하지 않도록 한다 (ADR-011 ②).</p>
 */
public sealed interface CreateShowcaseOutcome {

    Showcase showcase();

    /** 새 쇼케이스가 저장되었다. 후속 외부 I/O (3D 요청 등) 를 진행해야 한다. */
    record Created(Showcase showcase) implements CreateShowcaseOutcome {
    }

    /** content_hash dedup 히트. 기존 쇼케이스를 반환하며 후속 외부 I/O 는 스킵한다. */
    record Deduped(Showcase showcase) implements CreateShowcaseOutcome {
    }
}
