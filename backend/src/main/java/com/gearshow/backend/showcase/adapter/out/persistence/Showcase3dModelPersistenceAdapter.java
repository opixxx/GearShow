package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 쇼케이스 3D 모델 Persistence Adapter.
 *
 * <p><b>트랜잭션 전략</b>: 도메인이 불변 객체이므로 {@code save()} 는 dirty checking 대신
 * merge 경로를 탄다. Adapter 에 {@code @Transactional} 이 없으면 SimpleJpaRepository 가
 * 내부적으로 트랜잭션을 새로 열고, merge 때문에 SELECT + UPDATE 두 쿼리가 발생하며
 * 커넥션 획득/해제도 반복된다. 클래스 레벨에 선언하여 호출 측 트랜잭션에 참여하거나
 * 없으면 한 번만 열도록 한다.</p>
 */
@Repository
@RequiredArgsConstructor
@Transactional
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
    public int updateStatusIfCurrent(Long id, ModelStatus expected, ModelStatus newStatus) {
        return showcase3dModelJpaRepository.updateStatusIfCurrent(id, expected, newStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Showcase3dModel> findById(Long id) {
        return showcase3dModelJpaRepository.findById(id)
                .map(showcase3dModelMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Showcase3dModel> findByShowcaseId(Long showcaseId) {
        return showcase3dModelJpaRepository.findByShowcaseId(showcaseId)
                .map(showcase3dModelMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShowcaseId(Long showcaseId) {
        return showcase3dModelJpaRepository.existsByShowcaseId(showcaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findShowcaseIdsWithModel(List<Long> showcaseIds) {
        return new HashSet<>(showcase3dModelJpaRepository.findShowcaseIdsByShowcaseIds(showcaseIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Showcase3dModel> findPollableGeneratingTasks(int limit) {
        return showcase3dModelJpaRepository
                .findPollableGeneratingTasks(PageRequest.of(0, limit))
                .stream()
                .map(showcase3dModelMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Showcase3dModel> findStaleByStatus(ModelStatus status, Instant referenceAt, int limit) {
        return showcase3dModelJpaRepository
                .findByStatusAndRequestedBefore(status, referenceAt, PageRequest.of(0, limit))
                .stream()
                .map(showcase3dModelMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Showcase3dModel> findStaleGeneratingWithoutTaskId(Instant referenceAt, int limit) {
        return showcase3dModelJpaRepository
                .findStaleGeneratingWithoutTaskId(referenceAt, PageRequest.of(0, limit))
                .stream()
                .map(showcase3dModelMapper::toDomain)
                .toList();
    }
}
