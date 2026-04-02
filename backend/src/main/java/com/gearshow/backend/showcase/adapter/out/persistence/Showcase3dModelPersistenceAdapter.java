package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 쇼케이스 3D 모델 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class Showcase3dModelPersistenceAdapter implements Showcase3dModelPort {

    private final Showcase3dModelJpaRepository showcase3dModelJpaRepository;
    private final Showcase3dModelMapper showcase3dModelMapper;

    @Override
    public Showcase3dModel save(Showcase3dModel model) {
        Showcase3dModelJpaEntity entity = showcase3dModelMapper.toJpaEntity(model);
        Showcase3dModelJpaEntity saved = showcase3dModelJpaRepository.save(entity);
        return showcase3dModelMapper.toDomain(saved);
    }

    @Override
    public Optional<Showcase3dModel> findById(Long id) {
        return showcase3dModelJpaRepository.findById(id)
                .map(showcase3dModelMapper::toDomain);
    }

    @Override
    public Optional<Showcase3dModel> findByShowcaseId(Long showcaseId) {
        return showcase3dModelJpaRepository.findByShowcaseId(showcaseId)
                .map(showcase3dModelMapper::toDomain);
    }

    @Override
    public boolean existsByShowcaseId(Long showcaseId) {
        return showcase3dModelJpaRepository.findByShowcaseId(showcaseId).isPresent();
    }

    @Override
    public Set<Long> findShowcaseIdsWithModel(List<Long> showcaseIds) {
        return new HashSet<>(showcase3dModelJpaRepository.findShowcaseIdsByShowcaseIds(showcaseIds));
    }
}
