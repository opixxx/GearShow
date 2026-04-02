package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ShowcasePersistenceAdapter implements ShowcasePort {

    private final ShowcaseJpaRepository showcaseJpaRepository;
    private final ShowcaseMapper showcaseMapper;

    @Override
    public Showcase save(Showcase showcase) {
        ShowcaseJpaEntity entity = showcaseMapper.toJpaEntity(showcase);
        ShowcaseJpaEntity saved = showcaseJpaRepository.save(entity);
        return showcaseMapper.toDomain(saved);
    }

    @Override
    public Optional<Showcase> findById(Long id) {
        return showcaseJpaRepository.findById(id)
                .map(showcaseMapper::toDomain);
    }

    @Override
    public List<Showcase> findAllFirstPage(int size, List<Long> catalogItemIds,
                                           String keyword, Boolean isForSale,
                                           ConditionGrade conditionGrade) {
        return showcaseJpaRepository.findAllFirstPage(
                        catalogItemIds, keyword, isForSale, conditionGrade,
                        PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    @Override
    public List<Showcase> findAllWithCursor(Instant cursorCreatedAt, Long cursorId, int size,
                                            List<Long> catalogItemIds, String keyword,
                                            Boolean isForSale, ConditionGrade conditionGrade) {
        return showcaseJpaRepository.findAllWithCursor(
                        cursorCreatedAt, cursorId,
                        catalogItemIds, keyword, isForSale, conditionGrade,
                        PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    @Override
    public List<Showcase> findByOwnerIdFirstPage(Long ownerId, int size,
                                                  ShowcaseStatus showcaseStatus) {
        return showcaseJpaRepository.findByOwnerIdFirstPage(
                        ownerId, showcaseStatus,
                        PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    @Override
    public List<Showcase> findByOwnerIdWithCursor(Long ownerId, Instant cursorCreatedAt, Long cursorId,
                                                   int size, ShowcaseStatus showcaseStatus) {
        return showcaseJpaRepository.findByOwnerIdWithCursor(
                        ownerId, cursorCreatedAt, cursorId,
                        showcaseStatus, PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }
}
