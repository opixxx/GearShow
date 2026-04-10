package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.InsufficientModelSourceImagesException;
import com.gearshow.backend.showcase.application.exception.ModelAlreadyGeneratingException;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 3D 모델 생성 요청 Facade.
 *
 * <p>S3 키 URL 변환 등 순수 계산을 트랜잭션 밖에서 수행하고,
 * DB 저장 + Outbox 이벤트 기록은 {@link RequestModelGenerationService}의 단일 트랜잭션에 위임한다.
 * 실제 Kafka 발행은 Outbox Relay 스케줄러가 별도 스레드에서 수행하므로
 * 이 Facade 는 Kafka 에 대한 의존을 갖지 않는다.</p>
 */
@Service
@Primary
@RequiredArgsConstructor
public class RequestModelGenerationFacade implements RequestModelGenerationUseCase {

    private static final int MIN_SOURCE_IMAGES = 4;

    private final RequestModelGenerationService requestModelGenerationService;
    private final ShowcasePort showcasePort;
    private final Showcase3dModelPort showcase3dModelPort;
    private final ImageStoragePort imageStoragePort;

    @Override
    public ModelGenerationResult requestOnCreate(Long showcaseId, List<String> modelSourceImageKeys) {
        // 1. 검증 (트랜잭션 밖)
        validateSourceImageCount(modelSourceImageKeys);

        // 2. S3 키 → URL 변환 (트랜잭션 밖)
        List<String> imageUrls = modelSourceImageKeys.stream()
                .map(imageStoragePort::toUrl)
                .toList();

        // 3. DB 저장 + Outbox 이벤트 기록 (단일 트랜잭션)
        //    커밋 성공 = Outbox Relay 가 반드시 Kafka 발행을 시도함
        Showcase3dModel model = requestModelGenerationService.saveModelAndSourceImages(showcaseId, imageUrls);

        return new ModelGenerationResult(model.getId(), model.getModelStatus());
    }

    @Override
    public ModelGenerationResult requestRetry(Long showcaseId, Long ownerId,
                                               List<String> modelSourceImageKeys) {
        // 1. 검증 (트랜잭션 밖)
        validateOwner(showcaseId, ownerId);
        validateSourceImageCount(modelSourceImageKeys);
        validateNotAlreadyGenerating(showcaseId);

        // 2. S3 키 → URL 변환 (트랜잭션 밖)
        List<String> imageUrls = modelSourceImageKeys.stream()
                .map(imageStoragePort::toUrl)
                .toList();

        // 3. DB 저장 + Outbox 이벤트 기록 (단일 트랜잭션)
        Showcase3dModel model = requestModelGenerationService
                .resetOrCreateModelAndSaveSourceImages(showcaseId, imageUrls);

        return new ModelGenerationResult(model.getId(), model.getModelStatus());
    }

    private void validateSourceImageCount(List<String> keys) {
        if (keys == null || keys.size() < MIN_SOURCE_IMAGES) {
            throw new InsufficientModelSourceImagesException();
        }
    }

    private void validateOwner(Long showcaseId, Long ownerId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        showcase.validateOwner(ownerId);
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
