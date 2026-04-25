package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseOutcome;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.exception.InvalidImageKeyException;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 쇼케이스 등록 Facade (ADR-010 프로세스 분리 반영).
 */
@Service
@Primary
@RequiredArgsConstructor
public class CreateShowcaseFacade implements CreateShowcaseUseCase {

    private final CreateShowcaseService createShowcaseService;
    private final ImageStoragePort imageStoragePort;
    private final RequestModelGenerationUseCase requestModelGenerationUseCase;
    private final TripoCircuitGuard tripoCircuitGuard;

    @Override
    public CreateShowcaseResult create(CreateShowcaseCommand command,
                                        List<String> imageKeys,
                                        List<String> modelSourceImageKeys) {
        validateImageKeys(imageKeys, command.primaryImageIndex());
        rejectIfTripoCircuitOpen(modelSourceImageKeys);
        validateKeysExist(imageKeys);

        List<String> imageUrls = imageKeys.stream()
                .map(imageStoragePort::toUrl)
                .toList();
        CreateShowcaseOutcome outcome = createShowcaseService.saveShowcaseWithSpec(command, imageUrls);
        Showcase saved = outcome.showcase();

        // ADR-011 ②: content_hash 기반 dedup 히트 시엔 기존 쇼케이스 상태를 그대로 사용해
        // Tripo 중복 호출을 방지한다.
        ShowcaseModelStatus modelStatus = switch (outcome) {
            case CreateShowcaseOutcome.Created created ->
                    requestModelIfNeeded(created.showcase().getId(),
                            command.idempotencyKey(),
                            modelSourceImageKeys);
            case CreateShowcaseOutcome.Deduped deduped ->
                    deduped.showcase().isHas3dModel()
                            ? ShowcaseModelStatus.COMPLETED
                            : ShowcaseModelStatus.GENERATING;
        };

        return new CreateShowcaseResult(saved.getId(), modelStatus);
    }

    private void rejectIfTripoCircuitOpen(List<String> modelSourceImageKeys) {
        if (modelSourceImageKeys == null || modelSourceImageKeys.isEmpty()) {
            return;
        }
        tripoCircuitGuard.rejectIfOpen();
    }

    private void validateImageKeys(List<String> imageKeys, int primaryImageIndex) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            throw new MinImageRequiredException();
        }
        if (primaryImageIndex < 0 || primaryImageIndex >= imageKeys.size()) {
            throw new PrimaryImageRequiredException();
        }
    }

    private void validateKeysExist(List<String> imageKeys) {
        for (String key : imageKeys) {
            if (!imageStoragePort.exists(key)) {
                throw new InvalidImageKeyException();
            }
        }
    }

    private ShowcaseModelStatus requestModelIfNeeded(Long showcaseId,
                                                     String idempotencyKey,
                                                     List<String> modelSourceImageKeys) {
        if (modelSourceImageKeys == null || modelSourceImageKeys.isEmpty()) {
            return ShowcaseModelStatus.NONE;
        }
        ModelGenerationResult result = requestModelGenerationUseCase.requestOnCreate(
                showcaseId, idempotencyKey, modelSourceImageKeys);
        return result.modelStatus();
    }
}
