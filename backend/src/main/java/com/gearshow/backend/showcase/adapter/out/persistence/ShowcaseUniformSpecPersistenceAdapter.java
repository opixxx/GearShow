package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcaseUniformSpecPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseUniformSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 쇼케이스 유니폼 스펙 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ShowcaseUniformSpecPersistenceAdapter implements ShowcaseUniformSpecPort {

    private final ShowcaseUniformSpecJpaRepository repository;
    private final ShowcaseUniformSpecMapper mapper;

    @Override
    public ShowcaseUniformSpec save(ShowcaseUniformSpec spec) {
        ShowcaseUniformSpecJpaEntity entity = mapper.toJpaEntity(spec);
        ShowcaseUniformSpecJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ShowcaseUniformSpec> findByShowcaseId(Long showcaseId) {
        return repository.findByShowcaseId(showcaseId)
                .map(mapper::toDomain);
    }
}
