package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 쇼케이스 댓글 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ShowcaseCommentPersistenceAdapter implements ShowcaseCommentPort {

    private final ShowcaseCommentJpaRepository showcaseCommentJpaRepository;
    private final ShowcaseCommentMapper showcaseCommentMapper;

    @Override
    public ShowcaseComment save(ShowcaseComment comment) {
        ShowcaseCommentJpaEntity entity = showcaseCommentMapper.toJpaEntity(comment);
        ShowcaseCommentJpaEntity saved = showcaseCommentJpaRepository.save(entity);
        return showcaseCommentMapper.toDomain(saved);
    }

    @Override
    public Optional<ShowcaseComment> findById(Long id) {
        return showcaseCommentJpaRepository.findById(id)
                .map(showcaseCommentMapper::toDomain);
    }

    @Override
    public List<ShowcaseComment> findByShowcaseIdFirstPage(Long showcaseId, int size) {
        return showcaseCommentJpaRepository.findByShowcaseIdFirstPage(
                        showcaseId, PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseCommentMapper::toDomain)
                .toList();
    }

    @Override
    public List<ShowcaseComment> findByShowcaseIdWithCursor(Long showcaseId,
                                                              Instant cursorCreatedAt, Long cursorId,
                                                              int size) {
        return showcaseCommentJpaRepository.findByShowcaseIdWithCursor(
                        showcaseId, cursorCreatedAt, cursorId,
                        PageRequest.of(0, size + 1))
                .stream()
                .map(showcaseCommentMapper::toDomain)
                .toList();
    }

    @Override
    public int countActiveByShowcaseId(Long showcaseId) {
        return showcaseCommentJpaRepository.countActiveByShowcaseId(showcaseId);
    }

    @Override
    public Map<Long, Integer> countActiveByShowcaseIds(List<Long> showcaseIds) {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : showcaseCommentJpaRepository.countActiveByShowcaseIds(showcaseIds)) {
            Long showcaseId = (Long) row[0];
            Integer count = ((Number) row[1]).intValue();
            result.put(showcaseId, count);
        }
        return result;
    }

    @Override
    public void softDeleteAllByShowcaseId(Long showcaseId) {
        showcaseCommentJpaRepository.softDeleteAllByShowcaseId(showcaseId);
    }
}
