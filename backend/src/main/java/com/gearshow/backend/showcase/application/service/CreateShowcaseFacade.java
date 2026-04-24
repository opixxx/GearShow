package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseOutcome;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.InvalidImageKeyException;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 쇼케이스 등록 Facade.
 *
 * <p>S3 키 검증/URL 변환, 3D 모델 요청 등 외부 I/O를 트랜잭션 밖에서 수행하고,
 * DB 저장 구간만 {@link CreateShowcaseService}에 위임하여 트랜잭션 범위를 최소화한다.</p>
 *
 * <p>이미지 업로드는 클라이언트가 Presigned URL로 S3에 직접 수행하므로,
 * 서버는 S3 키 존재 여부를 검증한 후 URL로 변환하여 저장한다.</p>
 */
@Service
@Primary
@RequiredArgsConstructor
public class CreateShowcaseFacade implements CreateShowcaseUseCase {

    private final CreateShowcaseService createShowcaseService;
    private final ImageStoragePort imageStoragePort;
    private final RequestModelGenerationUseCase requestModelGenerationUseCase;
    private final TripoCircuitGuard tripoCircuitGuard;

    /**
     * 쇼케이스 등록 전체 흐름을 오케스트레이션한다.
     * <ol>
     *   <li>이미지 키 유효성 검증 (트랜잭션 밖)</li>
     *   <li>Tripo Circuit 가드 — 3D 생성 포함 요청이면 OPEN 상태 사전 거부 (503)</li>
     *   <li>S3 키 존재 여부 확인 (트랜잭션 밖)</li>
     *   <li>S3 키 → URL 변환 후 DB 저장 (트랜잭션 안)</li>
     *   <li>3D 모델 생성 비동기 요청 (트랜잭션 밖)</li>
     * </ol>
     */
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
        // Tripo 중복 호출을 방지한다 (이미 이전 생성 요청이 진행 중이거나 완료됨).
        ModelStatus modelStatus = switch (outcome) {
            case CreateShowcaseOutcome.Created created ->
                    requestModelIfNeeded(created.showcase().getId(),
                            command.idempotencyKey(),
                            modelSourceImageKeys);
            case CreateShowcaseOutcome.Deduped deduped ->
                    deduped.showcase().isHas3dModel() ? ModelStatus.COMPLETED : null;
        };

        return new CreateShowcaseResult(saved.getId(), modelStatus);
    }

    /**
     * 3D 생성이 포함된 요청이면 Tripo Circuit 상태를 사전 확인해 OPEN 이면 즉시 503.
     * {@code modelSourceImageKeys} 가 비어있으면 Tripo 와 무관하므로 건너뛴다.
     */
    private void rejectIfTripoCircuitOpen(List<String> modelSourceImageKeys) {
        if (modelSourceImageKeys == null || modelSourceImageKeys.isEmpty()) {
            return;
        }
        tripoCircuitGuard.rejectIfOpen();
    }

    /**
     * 이미지 키 목록이 비어있지 않고 primaryImageIndex가 유효한 범위인지 검증한다.
     */
    private void validateImageKeys(List<String> imageKeys, int primaryImageIndex) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            throw new MinImageRequiredException();
        }
        if (primaryImageIndex < 0 || primaryImageIndex >= imageKeys.size()) {
            throw new PrimaryImageRequiredException();
        }
    }

    /**
     * 모든 이미지 키가 S3에 실제로 존재하는지 확인한다.
     */
    private void validateKeysExist(List<String> imageKeys) {
        for (String key : imageKeys) {
            if (!imageStoragePort.exists(key)) {
                throw new InvalidImageKeyException();
            }
        }
    }

    /**
     * 3D 모델 소스 이미지 키가 있으면 비동기로 생성을 요청한다.
     */
    private ModelStatus requestModelIfNeeded(Long showcaseId,
                                              String idempotencyKey,
                                              List<String> modelSourceImageKeys) {
        if (modelSourceImageKeys == null || modelSourceImageKeys.isEmpty()) {
            return null;
        }
        ModelGenerationResult result = requestModelGenerationUseCase.requestOnCreate(
                showcaseId, idempotencyKey, modelSourceImageKeys);
        return result.modelStatus();
    }
}
