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
    public List<Showcase> findByKeywordFirstPage(String keyword, int size) {
        // PR-4 보강 (code-reviewer Critical #1): hasNext 판단을 위해 size + 1 조회.
        // PageInfo.of 가 data.size() <= expectedSize 로 hasNext 결정.
        return showcaseJpaRepository.findByKeywordFirstPage(
                        escapeLike(keyword), PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    @Override
    public List<Showcase> findByKeywordWithCursor(String keyword, Instant cursorCreatedAt,
                                                   Long cursorId, int size) {
        return showcaseJpaRepository.findByKeywordWithCursor(
                        escapeLike(keyword), cursorCreatedAt, cursorId,
                        PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseMapper::toDomain)
                .toList();
    }

    /**
     * LIKE wildcard ({@code %}, {@code _}, {@code \}) 를 리터럴로 escape (ADR-019 §D1).
     *
     * <p>사용자 입력의 {@code %} / {@code _} 가 LIKE 메타문자로 해석되어 의도와 다른 매칭 또는
     * DoS amplification (예: {@code ?keyword=%} 가 모든 행 매칭) 을 차단. JPQL 의 {@code ESCAPE '\\'}
     * 와 짝을 이뤄 동작.</p>
     */
    private static String escapeLike(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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
