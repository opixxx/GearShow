package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.application.port.out.BootsSpecPort;
import com.gearshow.backend.catalog.domain.model.BootsSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 축구화 스펙 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class BootsSpecPersistenceAdapter implements BootsSpecPort {

    private final BootsSpecJpaRepository bootsSpecJpaRepository;
    private final BootsSpecMapper bootsSpecMapper;

    @Override
    public BootsSpec save(BootsSpec bootsSpec) {
        BootsSpecJpaEntity entity = bootsSpecMapper.toJpaEntity(bootsSpec);
        BootsSpecJpaEntity saved = bootsSpecJpaRepository.save(entity);
        return bootsSpecMapper.toDomain(saved);
    }

    @Override
    public Optional<BootsSpec> findByCatalogItemId(Long catalogItemId) {
        return bootsSpecJpaRepository.findByCatalogItemId(catalogItemId)
                .map(bootsSpecMapper::toDomain);
    }
}
