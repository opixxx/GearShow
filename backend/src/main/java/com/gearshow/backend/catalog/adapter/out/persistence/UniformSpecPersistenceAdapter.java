package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.application.port.out.UniformSpecPort;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 유니폼 스펙 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class UniformSpecPersistenceAdapter implements UniformSpecPort {

    private final UniformSpecJpaRepository uniformSpecJpaRepository;
    private final UniformSpecMapper uniformSpecMapper;

    @Override
    public UniformSpec save(UniformSpec uniformSpec) {
        UniformSpecJpaEntity entity = uniformSpecMapper.toJpaEntity(uniformSpec);
        UniformSpecJpaEntity saved = uniformSpecJpaRepository.save(entity);
        return uniformSpecMapper.toDomain(saved);
    }

    @Override
    public Optional<UniformSpec> findByCatalogItemId(Long catalogItemId) {
        return uniformSpecJpaRepository.findByCatalogItemId(catalogItemId)
                .map(uniformSpecMapper::toDomain);
    }
}
