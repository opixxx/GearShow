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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    @Transactional
    public ModelGenerationResult requestOnCreate(Long showcaseId, List<MultipartFile> modelSourceImages) {
        validateSourceImageCount(modelSourceImages);

        // S3 업로드 후 URL 획득
        List<String> imageUrls = imageStoragePort.uploadAll(
                "showcases/" + showcaseId + "/model-sources", modelSourceImages);

        Showcase3dModel model = createAndSaveModel(showcaseId);
        saveSourceImages(model.getId(), imageUrls);
        modelGenerationPort.requestGeneration(model.getId(), showcaseId);

        return new ModelGenerationResult(model.getId(), model.getModelStatus());
    }

    @Override
    @Transactional
    public ModelGenerationResult requestRetry(Long showcaseId, Long ownerId,
                                               List<MultipartFile> modelSourceImages) {
        validateOwner(showcaseId, ownerId);
        validateSourceImageCount(modelSourceImages);
        validateNotAlreadyGenerating(showcaseId);

        // S3 업로드 후 URL 획득
        List<String> imageUrls = imageStoragePort.uploadAll(
                "showcases/" + showcaseId + "/model-sources", modelSourceImages);

        // 기존 모델이 있으면 재요청, 없으면 새로 생성
        Showcase3dModel model = showcase3dModelPort.findByShowcaseId(showcaseId)
                .map(this::resetToRequested)
                .orElseGet(() -> createAndSaveModel(showcaseId));

        saveSourceImages(model.getId(), imageUrls);
        modelGenerationPort.requestGeneration(model.getId(), showcaseId);

        return new ModelGenerationResult(model.getId(), model.getModelStatus());
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

    private void validateSourceImageCount(List<MultipartFile> images) {
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
