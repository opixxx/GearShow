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
import com.gearshow.backend.showcase.application.dto.UploadFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public CreateShowcaseResult create(CreateShowcaseCommand command,
                                        List<UploadFile> images,
                                        List<UploadFile> modelSourceImages) {
        // 1. 검증 (트랜잭션 불필요)
        validateImages(images, command.primaryImageIndex());

        // 2. S3 업로드 (트랜잭션 밖 — 외부 호출이므로 DB 커넥션 점유 방지)
        List<String> imageUrls = imageStoragePort.uploadAll("showcases", images);

        // 3. DB 저장 (트랜잭션)
        Showcase saved = saveShowcaseAndImages(command, imageUrls);

        // 4. 3D 모델 생성 요청 (트랜잭션 밖)
        ModelStatus modelStatus = null;
        if (command.hasModelSourceImages() && !modelSourceImages.isEmpty()) {
            ModelGenerationResult genResult = requestModelGenerationUseCase.requestOnCreate(
                    saved.getId(), modelSourceImages);
            modelStatus = genResult.modelStatus();
        }

        return new CreateShowcaseResult(saved.getId(), modelStatus);
    }

    /**
     * 쇼케이스와 이미지를 DB에 저장한다.
     */
    @Transactional
    protected Showcase saveShowcaseAndImages(CreateShowcaseCommand command, List<String> imageUrls) {
        Showcase showcase = createShowcase(command);
        Showcase saved = showcasePort.save(showcase);
        saveImages(saved.getId(), imageUrls, command.primaryImageIndex());
        return saved;
    }

    private Showcase createShowcase(CreateShowcaseCommand command) {
        return Showcase.create(
                command.ownerId(), command.catalogItemId(),
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
}
