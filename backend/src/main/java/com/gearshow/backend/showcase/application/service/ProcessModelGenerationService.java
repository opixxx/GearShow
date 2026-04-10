package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.ProcessModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 3D 모델 생성 처리 서비스.
 *
 * <p>비즈니스 흐름:
 * 1. 모델 조회 + GENERATING 상태 전환
 * 2. 외부 클라이언트(Tripo) 호출
 * 3. 성공: COMPLETED 저장 + has3dModel = true
 * 4. 실패: FAILED 저장 + has3dModel = false
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessModelGenerationService implements ProcessModelGenerationUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ShowcasePort showcasePort;
    private final ModelGenerationClient modelGenerationClient;

    @Override
    public void process(Long showcase3dModelId, Long showcaseId) {
        showcase3dModelPort.findById(showcase3dModelId)
                .ifPresentOrElse(
                        model -> generateAndPersist(startGenerating(model), showcaseId),
                        () -> log.warn("3D 모델을 찾을 수 없습니다 - showcase3dModelId: {}",
                                showcase3dModelId)
                );
    }

    /**
     * 모델을 GENERATING 상태로 전환하고 저장한다.
     */
    private Showcase3dModel startGenerating(Showcase3dModel model) {
        return showcase3dModelPort.save(model.startGenerating());
    }

    /**
     * 외부 클라이언트를 호출하고 결과를 DB에 반영한다.
     * 외부 호출 / DB 저장 예외만 좁게 잡아 FAILED로 전환한다.
     */
    private void generateAndPersist(Showcase3dModel model, Long showcaseId) {
        GenerationResult result;
        try {
            result = modelGenerationClient.generate(model.getId(), showcaseId);
        } catch (RestClientException | DataAccessException e) {
            markAsFailed(model, showcaseId, "외부 호출 또는 저장 실패");
            log.error("3D 모델 생성 외부 호출 실패 - showcase3dModelId: {}", model.getId(), e);
            return;
        }

        if (result.success()) {
            persistCompleted(model, showcaseId, result);
        } else {
            markAsFailed(model, showcaseId, result.failureReason());
            log.warn("3D 모델 생성 실패 - showcase3dModelId: {}, 사유: {}",
                    model.getId(), result.failureReason());
        }
    }

    /**
     * 모델을 COMPLETED 상태로 저장하고 쇼케이스의 has3dModel 플래그를 동기화한다.
     */
    private void persistCompleted(Showcase3dModel model, Long showcaseId, GenerationResult result) {
        Showcase3dModel completed = model.complete(result.modelFileUrl(), result.previewImageUrl());
        showcase3dModelPort.save(completed);
        showcasePort.updateHas3dModel(showcaseId, true);
        log.info("3D 모델 생성 완료 - showcase3dModelId: {}", model.getId());
    }

    /**
     * 모델을 FAILED 상태로 저장하고 쇼케이스의 has3dModel 플래그를 동기화한다.
     */
    private void markAsFailed(Showcase3dModel model, Long showcaseId, String reason) {
        Showcase3dModel failed = model.fail(reason);
        showcase3dModelPort.save(failed);
        showcasePort.updateHas3dModel(showcaseId, false);
    }
}
