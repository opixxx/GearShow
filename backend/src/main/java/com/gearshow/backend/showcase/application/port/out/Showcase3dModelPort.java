package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.Showcase3dModel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 쇼케이스 3D 모델(완성품) Outbound Port (ADR-010).
 *
 * <p>프로세스 상태는 {@code model_generation_workflow} 가 담당하므로 본 포트는 완성품 CRUD 만
 * 노출한다. {@link #save(Showcase3dModel)} 은 UPSERT 의미 — {@code showcase_id} 기준으로
 * 이미 행이 있으면 id 를 찾아 UPDATE, 없으면 INSERT. 호출자는 완성품 신규/재생성 여부를
 * 신경 쓰지 않는다.</p>
 */
public interface Showcase3dModelPort {

    /**
     * 3D 모델(완성품) 을 UPSERT 한다. {@code showcase_id} 가 이미 존재하면 해당 행을 최신
     * 산출물로 덮어쓰고, 없으면 새 행을 INSERT 한다.
     */
    Showcase3dModel save(Showcase3dModel model);

    /**
     * ID 로 3D 모델을 조회한다.
     */
    Optional<Showcase3dModel> findById(Long id);

    /**
     * 쇼케이스 ID 로 3D 모델(완성품) 을 조회한다. 완성 전에는 Optional.empty.
     */
    Optional<Showcase3dModel> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID 에 해당하는 완성품 존재 여부.
     */
    boolean existsByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스 중 완성품이 존재하는 쇼케이스 ID 집합을 반환한다.
     */
    Set<Long> findShowcaseIdsWithModel(List<Long> showcaseIds);
}
