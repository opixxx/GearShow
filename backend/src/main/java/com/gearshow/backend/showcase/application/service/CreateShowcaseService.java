package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseBootsSpecPort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseUniformSpecPort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseBootsSpec;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseUniformSpec;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import com.gearshow.backend.showcase.application.dto.UploadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 쇼케이스 등록 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class CreateShowcaseService implements CreateShowcaseUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final ImageStoragePort imageStoragePort;
    private final ShowcaseBootsSpecPort showcaseBootsSpecPort;
    private final ShowcaseUniformSpecPort showcaseUniformSpecPort;
    private final RequestModelGenerationUseCase requestModelGenerationUseCase;

    @Override
    @Transactional
    public CreateShowcaseResult create(CreateShowcaseCommand command,
                                        List<UploadFile> images,
                                        List<UploadFile> modelSourceImages) {
        validateImages(images, command.primaryImageIndex());

        List<String> imageUrls = imageStoragePort.uploadAll("showcases", images);
        Showcase saved = saveShowcaseWithSpec(command, imageUrls);
        ModelStatus modelStatus = requestModelIfNeeded(saved.getId(), command, modelSourceImages);

        return new CreateShowcaseResult(saved.getId(), modelStatus);
    }

    /**
     * 3D 모델 소스 이미지가 있으면 생성을 비동기 요청한다.
     */
    private ModelStatus requestModelIfNeeded(Long showcaseId,
                                              CreateShowcaseCommand command,
                                              List<UploadFile> modelSourceImages) {
        if (!command.hasModelSourceImages() || modelSourceImages.isEmpty()) {
            return null;
        }
        ModelGenerationResult result = requestModelGenerationUseCase.requestOnCreate(
                showcaseId, modelSourceImages);
        return result.modelStatus();
    }

    /**
     * 쇼케이스, 이미지, 스펙을 DB에 저장한다.
     */
    protected Showcase saveShowcaseWithSpec(CreateShowcaseCommand command, List<String> imageUrls) {
        Showcase showcase = createShowcase(command);
        Showcase saved = showcasePort.save(showcase);
        saveImages(saved.getId(), imageUrls, command.primaryImageIndex());
        saveSpec(saved.getId(), command.category(), command);
        return saved;
    }

    private Showcase createShowcase(CreateShowcaseCommand command) {
        return Showcase.create(
                command.ownerId(), command.catalogItemId(),
                command.category(), command.brand(), command.modelCode(),
                command.title(), command.description(),
                command.userSize(), command.conditionGrade(),
                command.wearCount(), command.isForSale());
    }

    /**
     * 이미지 최소 1장, primaryImageIndex 범위 검증.
     */
    private void validateImages(List<UploadFile> images, int primaryImageIndex) {
        if (images == null || images.isEmpty()) {
            throw new MinImageRequiredException();
        }
        if (primaryImageIndex < 0 || primaryImageIndex >= images.size()) {
            throw new PrimaryImageRequiredException();
        }
    }

    private void saveImages(Long showcaseId, List<String> imageUrls, int primaryImageIndex) {
        List<ShowcaseImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(ShowcaseImage.create(
                    showcaseId, imageUrls.get(i),
                    i + 1, i == primaryImageIndex));
        }
        showcaseImagePort.saveAll(images);
    }

    /**
     * 카테고리에 따라 쇼케이스 스펙을 저장한다.
     */
    private void saveSpec(Long showcaseId, Category category, CreateShowcaseCommand command) {
        if (category == Category.BOOTS && command.bootsSpec() != null) {
            saveBootsSpec(showcaseId, command.bootsSpec());
        } else if (category == Category.UNIFORM && command.uniformSpec() != null) {
            saveUniformSpec(showcaseId, command.uniformSpec());
        }
    }

    private void saveBootsSpec(Long showcaseId, CreateShowcaseCommand.BootsSpecCommand spec) {
        Instant now = Instant.now();
        ShowcaseBootsSpec bootsSpec = ShowcaseBootsSpec.builder()
                .showcaseId(showcaseId)
                .studType(spec.studType())
                .siloName(spec.siloName())
                .releaseYear(spec.releaseYear())
                .surfaceType(spec.surfaceType())
                .extraSpecJson(spec.extraSpecJson())
                .createdAt(now)
                .updatedAt(now)
                .build();
        showcaseBootsSpecPort.save(bootsSpec);
    }

    private void saveUniformSpec(Long showcaseId, CreateShowcaseCommand.UniformSpecCommand spec) {
        Instant now = Instant.now();
        ShowcaseUniformSpec uniformSpec = ShowcaseUniformSpec.builder()
                .showcaseId(showcaseId)
                .clubName(spec.clubName())
                .season(spec.season())
                .league(spec.league())
                .kitType(spec.kitType())
                .extraSpecJson(spec.extraSpecJson())
                .createdAt(now)
                .updatedAt(now)
                .build();
        showcaseUniformSpecPort.save(uniformSpec);
    }
}
