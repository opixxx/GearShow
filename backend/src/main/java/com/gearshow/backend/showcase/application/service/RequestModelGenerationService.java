package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.InsufficientModelSourceImagesException;
import com.gearshow.backend.showcase.application.exception.ModelAlreadyGeneratingException;
import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.AngleType;
import com.gearshow.backend.showcase.application.dto.UploadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D 모델 생성 요청 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class RequestModelGenerationService implements RequestModelGenerationUseCase {

    private static final int MIN_SOURCE_IMAGES = 4;
    private static final String GENERATION_PROVIDER = "fake-tripo";
    private static final AngleType[] ANGLE_TYPES = AngleType.values();

    private final ShowcasePort showcasePort;
    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelSourceImagePort modelSourceImagePort;
    private final ModelGenerationPort modelGenerationPort;
    private final ImageStoragePort imageStoragePort;

    @Override
    public ModelGenerationResult requestOnCreate(Long showcaseId, List<UploadFile> modelSourceImages) {
        // 1. 검증
        validateSourceImageCount(modelSourceImages);

        // 2. S3 업로드 (트랜잭션 밖)
        List<String> imageUrls = imageStoragePort.uploadAll(
                "showcases/" + showcaseId + "/model-sources", modelSourceImages);

        // 3. DB 저장 (트랜잭션)
        Showcase3dModel model = saveModelAndSourceImages(showcaseId, imageUrls);

        // 4. Kafka 발행 (트랜잭션 밖)
        modelGenerationPort.requestGeneration(model.getId(), showcaseId);

        return new ModelGenerationResult(model.getId(), model.getModelStatus());
    }

    @Override
    public ModelGenerationResult requestRetry(Long showcaseId, Long ownerId,
                                               List<UploadFile> modelSourceImages) {
        // 1. 검증
        validateOwner(showcaseId, ownerId);
        validateSourceImageCount(modelSourceImages);
        validateNotAlreadyGenerating(showcaseId);

        // 2. S3 업로드 (트랜잭션 밖)
        List<String> imageUrls = imageStoragePort.uploadAll(
                "showcases/" + showcaseId + "/model-sources", modelSourceImages);

        // 3. DB 저장 (트랜잭션)
        Showcase3dModel model = resetOrCreateModelAndSaveSourceImages(showcaseId, imageUrls);

        // 4. Kafka 발행 (트랜잭션 밖)
        modelGenerationPort.requestGeneration(model.getId(), showcaseId);

        return new ModelGenerationResult(model.getId(), model.getModelStatus());
    }

    @Transactional
    protected Showcase3dModel saveModelAndSourceImages(Long showcaseId, List<String> imageUrls) {
        Showcase3dModel model = createAndSaveModel(showcaseId);
        saveSourceImages(model.getId(), imageUrls);
        return model;
    }

    @Transactional
    protected Showcase3dModel resetOrCreateModelAndSaveSourceImages(Long showcaseId, List<String> imageUrls) {
        Showcase3dModel model = showcase3dModelPort.findByShowcaseId(showcaseId)
                .map(this::resetToRequested)
                .orElseGet(() -> createAndSaveModel(showcaseId));
        saveSourceImages(model.getId(), imageUrls);
        return model;
    }

    private Showcase3dModel createAndSaveModel(Long showcaseId) {
        Showcase3dModel model = Showcase3dModel.request(showcaseId, GENERATION_PROVIDER);
        return showcase3dModelPort.save(model);
    }

    /**
     * FAILED 상태의 모델을 REQUESTED로 재설정한다.
     */
    private Showcase3dModel resetToRequested(Showcase3dModel existingModel) {
        Showcase3dModel newRequest = Showcase3dModel.request(
                existingModel.getShowcaseId(), GENERATION_PROVIDER);
        Showcase3dModel withId = Showcase3dModel.builder()
                .id(existingModel.getId())
                .showcaseId(existingModel.getShowcaseId())
                .modelStatus(newRequest.getModelStatus())
                .generationProvider(newRequest.getGenerationProvider())
                .requestedAt(newRequest.getRequestedAt())
                .createdAt(existingModel.getCreatedAt())
                .build();
        return showcase3dModelPort.save(withId);
    }

    private void saveSourceImages(Long showcase3dModelId, List<String> imageUrls) {
        List<ModelSourceImage> sourceImages = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            AngleType angleType = ANGLE_TYPES[i % ANGLE_TYPES.length];
            sourceImages.add(ModelSourceImage.create(
                    showcase3dModelId, imageUrls.get(i), angleType, i + 1));
        }
        modelSourceImagePort.saveAll(sourceImages);
    }

    private void validateSourceImageCount(List<UploadFile> images) {
        if (images == null || images.size() < MIN_SOURCE_IMAGES) {
            throw new InsufficientModelSourceImagesException();
        }
    }

    private void validateOwner(Long showcaseId, Long ownerId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        if (!showcase.getOwnerId().equals(ownerId)) {
            throw new NotOwnerShowcaseException();
        }
    }

    private void validateNotAlreadyGenerating(Long showcaseId) {
        showcase3dModelPort.findByShowcaseId(showcaseId)
                .ifPresent(model -> {
                    if (model.isGenerating()) {
                        throw new ModelAlreadyGeneratingException();
                    }
                });
    }
}
