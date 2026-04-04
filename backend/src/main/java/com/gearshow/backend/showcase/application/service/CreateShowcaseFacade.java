package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.dto.UploadFile;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 쇼케이스 등록 Facade.
 *
 * <p>외부 I/O(S3 업로드, 3D 모델 요청)를 트랜잭션 밖에서 수행하고,
 * DB 저장 구간만 {@link CreateShowcaseService}에 위임하여 트랜잭션 범위를 최소화한다.</p>
 */
@Service
@Primary
@RequiredArgsConstructor
public class CreateShowcaseFacade implements CreateShowcaseUseCase {

    private final CreateShowcaseService createShowcaseService;
    private final ImageStoragePort imageStoragePort;
    private final RequestModelGenerationUseCase requestModelGenerationUseCase;

    /**
     * 쇼케이스 등록 전체 흐름을 오케스트레이션한다.
     * <ol>
     *   <li>이미지 검증 (트랜잭션 밖)</li>
     *   <li>S3 이미지 업로드 (트랜잭션 밖, 외부 I/O)</li>
     *   <li>쇼케이스 + 이미지 + 스펙 DB 저장 (트랜잭션 안)</li>
     *   <li>3D 모델 생성 비동기 요청 (트랜잭션 밖, 외부 I/O)</li>
     * </ol>
     */
    @Override
    public CreateShowcaseResult create(CreateShowcaseCommand command,
                                        List<UploadFile> images,
                                        List<UploadFile> modelSourceImages) {
        // 1. 검증 (트랜잭션 밖)
        validateImages(images, command.primaryImageIndex());

        // 2. S3 이미지 업로드 (트랜잭션 밖)
        List<String> imageUrls = imageStoragePort.uploadAll("showcases", images);

        // 3. DB 저장 (트랜잭션 안)
        Showcase saved = createShowcaseService.saveShowcaseWithSpec(command, imageUrls);

        // 4. 3D 모델 비동기 요청 (트랜잭션 밖)
        ModelStatus modelStatus = requestModelIfNeeded(saved.getId(), command, modelSourceImages);

        return new CreateShowcaseResult(saved.getId(), modelStatus);
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
}
