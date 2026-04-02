package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 3D 모델 소스 이미지 Persistence Adapter.
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
    public List<ModelSourceImage> findByShowcase3dModelId(Long showcase3dModelId) {
        return modelSourceImageJpaRepository.findByShowcase3dModelId(showcase3dModelId).stream()
                .map(modelSourceImageMapper::toDomain)
                .toList();
    }

    @Override
    public int countByShowcase3dModelId(Long showcase3dModelId) {
        return modelSourceImageJpaRepository.findByShowcase3dModelId(showcase3dModelId).size();
    }
}
