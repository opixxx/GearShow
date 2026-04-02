package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final RequestModelGenerationUseCase requestModelGenerationUseCase;

    @Override
    @Transactional
    public CreateShowcaseResult create(CreateShowcaseCommand command,
                                        List<MultipartFile> images,
                                        List<MultipartFile> modelSourceImages) {
        validateImages(images, command.primaryImageIndex());

        Showcase showcase = createShowcase(command);
        Showcase saved = showcasePort.save(showcase);

        // S3 업로드 후 이미지 저장
        List<String> imageUrls = imageStoragePort.uploadAll("showcases", images);
        saveImages(saved.getId(), imageUrls, command.primaryImageIndex());

        // 3D 모델 소스 이미지가 있으면 비동기 생성 요청
        ModelStatus modelStatus = null;
        if (command.hasModelSourceImages() && !modelSourceImages.isEmpty()) {
            ModelGenerationResult genResult = requestModelGenerationUseCase.requestOnCreate(
                    saved.getId(), modelSourceImages);
            modelStatus = genResult.modelStatus();
        }

        return new CreateShowcaseResult(saved.getId(), modelStatus);
    }

    private Showcase createShowcase(CreateShowcaseCommand command) {
        Showcase showcase = Showcase.create(
                command.ownerId(), command.catalogItemId(),
                command.title(), command.conditionGrade());

        // description, userSize, wearCount, isForSale는 Builder로 설정
        return Showcase.builder()
                .id(null)
                .ownerId(command.ownerId())
                .catalogItemId(command.catalogItemId())
                .title(command.title())
                .description(command.description())
                .userSize(command.userSize())
                .conditionGrade(command.conditionGrade())
                .wearCount(command.wearCount())
                .forSale(command.isForSale())
                .status(showcase.getStatus())
                .createdAt(showcase.getCreatedAt())
                .updatedAt(showcase.getUpdatedAt())
                .build();
    }

    /**
     * 이미지 최소 1장, primaryImageIndex 범위 검증.
     */
    private void validateImages(List<MultipartFile> images, int primaryImageIndex) {
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
}
