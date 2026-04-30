package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.MarkShowcaseHas3dModelUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MarkShowcaseHas3dModelUseCase} 구현체.
 *
 * <p>{@link ShowcasePort#updateHas3dModel} 의 {@code @Modifying} 쿼리로 단일 컬럼 UPDATE 만
 * 수행한다. Aggregate 전체를 로드하지 않으므로 race window 가 좁고, 다른 컬럼 동시 수정과
 * lost-update 충돌이 발생하지 않는다.</p>
 *
 * <p>본 서비스는 자체 TX 경계 ({@code @Transactional}) 를 가진다. AFTER_COMMIT 리스너에서
 * 호출되므로 호출 시점에는 상위 TX 가 이미 커밋된 상태 — 새 TX 가 열려야 정상 동작한다.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MarkShowcaseHas3dModelService implements MarkShowcaseHas3dModelUseCase {

    private final ShowcasePort showcasePort;

    @Override
    public void markHas3dModel(Long showcaseId) {
        int affected = showcasePort.updateHas3dModel(showcaseId, true);
        if (affected == 0) {
            log.warn("Showcase.has_3d_model 갱신 affected=0 — showcaseId: {} 가 존재하지 않거나 "
                    + "DELETED 상태. showcase_3d_model 과의 정합성 점검 필요.", showcaseId);
            return;
        }
        log.info("Showcase.has_3d_model 동기화 완료 — showcaseId: {}", showcaseId);
    }
}
