package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 쇼케이스 3D 모델(완성품) Persistence Adapter (ADR-010).
 *
 * <p>{@link #save(Showcase3dModel)} 은 UPSERT — {@code showcase_id} UNIQUE 제약을 기준으로
 * 기존 행을 찾아 필드 업데이트 또는 신규 INSERT 를 명시적으로 분기한다. JPA merge 나
 * {@code @SQLInsert} ON DUPLICATE 같은 암묵 경로를 피하고 쿼리 발생을 관찰 가능하게 남긴다.</p>
 */
@Repository
@RequiredArgsConstructor
@Transactional
public class Showcase3dModelPersistenceAdapter implements Showcase3dModelPort {

    private final Showcase3dModelJpaRepository showcase3dModelJpaRepository;
    private final Showcase3dModelMapper showcase3dModelMapper;

    @Override
    public Showcase3dModel save(Showcase3dModel model) {
        Optional<Showcase3dModelJpaEntity> existing =
                showcase3dModelJpaRepository.findByShowcaseId(model.getShowcaseId());
        Showcase3dModelJpaEntity target;
        if (existing.isPresent()) {
            target = existing.get();
            target.replaceArtifact(
                    model.getModelFileUrl(),
                    model.getPreviewImageUrl(),
                    model.getGeneratedAt() != null ? model.getGeneratedAt() : Instant.now(),
                    Instant.now()
            );
        } else {
            target = showcase3dModelMapper.toJpaEntity(model);
        }
        Showcase3dModelJpaEntity saved = showcase3dModelJpaRepository.saveAndFlush(target);
        return showcase3dModelMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Showcase3dModel> findById(Long id) {
        return showcase3dModelJpaRepository.findById(id)
                .map(showcase3dModelMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Showcase3dModel> findByShowcaseId(Long showcaseId) {
        return showcase3dModelJpaRepository.findByShowcaseId(showcaseId)
                .map(showcase3dModelMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShowcaseId(Long showcaseId) {
        return showcase3dModelJpaRepository.existsByShowcaseId(showcaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findShowcaseIdsWithModel(List<Long> showcaseIds) {
        return new HashSet<>(showcase3dModelJpaRepository.findShowcaseIdsByShowcaseIds(showcaseIds));
    }
}
