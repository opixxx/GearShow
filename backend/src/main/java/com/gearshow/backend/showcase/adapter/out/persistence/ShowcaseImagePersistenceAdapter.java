package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 쇼케이스 이미지 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ShowcaseImagePersistenceAdapter implements ShowcaseImagePort {

    private final ShowcaseImageJpaRepository showcaseImageJpaRepository;
    private final ShowcaseImageMapper showcaseImageMapper;

    @Override
    public ShowcaseImage save(ShowcaseImage image) {
        ShowcaseImageJpaEntity entity = showcaseImageMapper.toJpaEntity(image);
        ShowcaseImageJpaEntity saved = showcaseImageJpaRepository.save(entity);
        return showcaseImageMapper.toDomain(saved);
    }

    @Override
    public List<ShowcaseImage> saveAll(List<ShowcaseImage> images) {
        List<ShowcaseImageJpaEntity> entities = images.stream()
                .map(showcaseImageMapper::toJpaEntity)
                .toList();
        return showcaseImageJpaRepository.saveAll(entities).stream()
                .map(showcaseImageMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ShowcaseImage> findById(Long id) {
        return showcaseImageJpaRepository.findById(id)
                .map(showcaseImageMapper::toDomain);
    }

    @Override
    public List<ShowcaseImage> findByShowcaseId(Long showcaseId) {
        return showcaseImageJpaRepository.findByShowcaseId(showcaseId).stream()
                .map(showcaseImageMapper::toDomain)
                .toList();
    }

    @Override
    public int countByShowcaseId(Long showcaseId) {
        return showcaseImageJpaRepository.countByShowcaseId(showcaseId);
    }

    @Override
    public void deleteById(Long id) {
        showcaseImageJpaRepository.deleteById(id);
    }

    @Override
    public String findPrimaryImageUrlByShowcaseId(Long showcaseId) {
        return showcaseImageJpaRepository.findPrimaryByShowcaseId(showcaseId)
                .map(ShowcaseImageJpaEntity::getImageUrl)
                .orElse(null);
    }

    @Override
    public Map<Long, String> findPrimaryImageUrlsByShowcaseIds(List<Long> showcaseIds) {
        return showcaseImageJpaRepository.findPrimaryByShowcaseIds(showcaseIds).stream()
                .collect(Collectors.toMap(
                        ShowcaseImageJpaEntity::getShowcaseId,
                        ShowcaseImageJpaEntity::getImageUrl));
    }
}
