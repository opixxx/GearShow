package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.exception.InsufficientModelSourceImagesException;
import com.gearshow.backend.showcase.application.exception.ModelAlreadyGeneratingException;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 3D 모델 생성 요청 Facade (ADR-010 프로세스 분리 반영).
 *
 * <p>외부 I/O(S3 키 URL 변환 등)는 트랜잭션 밖에서 수행하고, DB 저장 + Outbox 이벤트 기록은
 * {@link RequestModelGenerationService} 의 단일 트랜잭션에 위임한다.</p>
 *
 * <p><b>"이미 생성 중" 판정</b>: Showcase3dModel 이 완성품 전용이라 이 체크 역시 workflow 테이블로
 * 이동한다. 최신 attempt 의 {@code current_step} 이 {@code COMPLETED}/{@code FAILED} 가 아니면
 * 재시도를 차단해 이중 호출을 방지한다.</p>
 */
@Service
@Primary
@RequiredArgsConstructor
public class RequestModelGenerationFacade implements RequestModelGenerationUseCase {

    private static final int MIN_SOURCE_IMAGES = 4;

    private final RequestModelGenerationService requestModelGenerationService;
    private final ShowcasePort showcasePort;
    private final ModelGenerationWorkflowPort modelGenerationWorkflowPort;
    private final ImageStoragePort imageStoragePort;
    private final TripoCircuitGuard tripoCircuitGuard;

    @Override
    public ModelGenerationResult requestOnCreate(Long showcaseId,
                                                  String idempotencyKey,
                                                  List<String> modelSourceImageKeys) {
        validateSourceImageCount(modelSourceImageKeys);

        List<String> imageUrls = modelSourceImageKeys.stream()
                .map(imageStoragePort::toUrl)
                .toList();

        return requestModelGenerationService.saveSourceImagesAndRequest(
                showcaseId, idempotencyKey, imageUrls);
    }

    @Override
    public ModelGenerationResult requestRetry(Long showcaseId,
                                               Long ownerId,
                                               String idempotencyKey,
                                               List<String> modelSourceImageKeys) {
        validateOwner(showcaseId, ownerId);
        validateSourceImageCount(modelSourceImageKeys);
        validateNotAlreadyGenerating(showcaseId);

        tripoCircuitGuard.rejectIfOpen();

        List<String> imageUrls = modelSourceImageKeys.stream()
                .map(imageStoragePort::toUrl)
                .toList();

        return requestModelGenerationService.resetSourceImagesAndRequestRetry(
                showcaseId, idempotencyKey, imageUrls);
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
        Optional<WorkflowSnapshot> latest =
                modelGenerationWorkflowPort.findLatestSnapshotByShowcaseId(showcaseId);
        if (latest.isEmpty()) {
            return;
        }
        WorkflowStep step = latest.get().currentStep();
        if (step != WorkflowStep.COMPLETED && step != WorkflowStep.FAILED) {
            throw new ModelAlreadyGeneratingException();
        }
    }
}
