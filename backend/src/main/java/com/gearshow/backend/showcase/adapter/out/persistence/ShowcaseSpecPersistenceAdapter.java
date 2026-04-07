package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcaseSpecPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 쇼케이스 스펙 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ShowcaseSpecPersistenceAdapter implements ShowcaseSpecPort {

    private final ShowcaseSpecJpaRepository repository;
    private final ShowcaseSpecMapper mapper;

    @Override
    public ShowcaseSpec save(ShowcaseSpec spec) {
        ShowcaseSpecJpaEntity entity = mapper.toJpaEntity(spec);
        ShowcaseSpecJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ShowcaseSpec> findByShowcaseId(Long showcaseId) {
        return repository.findByShowcaseId(showcaseId)
                .map(mapper::toDomain);
    }

    @Override
    public Map<Long, ShowcaseSpec> findByShowcaseIds(List<Long> showcaseIds) {
        return repository.findByShowcaseIdIn(showcaseIds).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toMap(ShowcaseSpec::getShowcaseId, spec -> spec));
    }
}
