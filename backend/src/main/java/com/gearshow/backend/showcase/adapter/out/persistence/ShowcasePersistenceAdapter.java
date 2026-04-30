package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ContentHash;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
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
    public List<Showcase> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return showcaseJpaRepository.findAllById(ids)
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    @Override
    public List<Showcase> findAllFirstPage(int size) {
        return showcaseJpaRepository.findAllFirstPage(PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    @Override
    public List<Showcase> findAllWithCursor(Instant cursorCreatedAt, Long cursorId, int size) {
        return showcaseJpaRepository.findAllWithCursor(
                        cursorCreatedAt, cursorId, PageRequest.of(0, size + 1))
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

    @Override
    public int updateHas3dModel(Long showcaseId, boolean has3dModel) {
        return showcaseJpaRepository.updateHas3dModel(showcaseId, has3dModel);
    }

    @Override
    public Optional<Showcase> findRecentByOwnerAndContentHash(Long ownerId, ContentHash contentHash,
                                                              Instant createdAfter) {
        return showcaseJpaRepository
                .findTop1ByOwnerIdAndContentHashAndCreatedAtAfterOrderByCreatedAtDesc(
                        ownerId, contentHash.value(), createdAfter)
                .map(showcaseMapper::toDomain);
    }
}
