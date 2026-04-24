package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 3D 모델 소스 이미지 Persistence Adapter (ADR-010 showcase_id 직접 참조).
 */
@Repository
@RequiredArgsConstructor
public class ModelSourceImagePersistenceAdapter implements ModelSourceImagePort {

    private final ModelSourceImageJpaRepository modelSourceImageJpaRepository;
    private final ModelSourceImageMapper modelSourceImageMapper;

    @Override
    public List<ModelSourceImage> saveAll(List<ModelSourceImage> images) {
        List<ModelSourceImageJpaEntity> entities = images.stream()
                .map(modelSourceImageMapper::toJpaEntity)
                .toList();
        return modelSourceImageJpaRepository.saveAll(entities).stream()
                .map(modelSourceImageMapper::toDomain)
                .toList();
    }

    @Override
    public List<ModelSourceImage> findByShowcaseId(Long showcaseId) {
        return modelSourceImageJpaRepository.findByShowcaseIdOrderBySortOrderAsc(showcaseId).stream()
                .map(modelSourceImageMapper::toDomain)
                .toList();
    }

    @Override
    public int countByShowcaseId(Long showcaseId) {
        return modelSourceImageJpaRepository.countByShowcaseId(showcaseId);
    }

    @Override
    public List<String> findImageUrlsByShowcaseId(Long showcaseId) {
        return modelSourceImageJpaRepository.findImageUrlsByShowcaseId(showcaseId);
    }
}
