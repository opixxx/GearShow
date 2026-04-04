package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcaseBootsSpecPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseBootsSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 쇼케이스 축구화 스펙 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ShowcaseBootsSpecPersistenceAdapter implements ShowcaseBootsSpecPort {

    private final ShowcaseBootsSpecJpaRepository repository;
    private final ShowcaseBootsSpecMapper mapper;

    @Override
    public ShowcaseBootsSpec save(ShowcaseBootsSpec spec) {
        ShowcaseBootsSpecJpaEntity entity = mapper.toJpaEntity(spec);
        ShowcaseBootsSpecJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ShowcaseBootsSpec> findByShowcaseId(Long showcaseId) {
        return repository.findByShowcaseId(showcaseId)
                .map(mapper::toDomain);
    }
}
