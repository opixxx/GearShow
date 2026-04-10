package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.AngleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D 모델 생성 요청 트랜잭션 서비스.
 *
 * <p>DB 저장과 Outbox 이벤트 기록을 단일 트랜잭션으로 묶어 원자성을 보장한다.
 * 실제 Kafka 발행은 Outbox Relay 스케줄러가 별도 스레드에서 수행한다.
 * 외부 I/O(S3 키 변환 등)는 {@link RequestModelGenerationFacade}에서 트랜잭션 밖에서 수행한다.</p>
 */
@Service
@RequiredArgsConstructor
public class RequestModelGenerationService {

    private static final String GENERATION_PROVIDER = "fake-tripo";
    private static final AngleType[] ANGLE_TYPES = AngleType.values();

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelSourceImagePort modelSourceImagePort;
    private final ModelGenerationEventPublisher modelGenerationEventPublisher;

    /**
     * 신규 3D 모델과 소스 이미지를 저장하고 Outbox 이벤트를 기록한다.
     *
     * <p>세 작업이 같은 트랜잭션 안에서 수행되어, 커밋 성공 = 이후 Kafka 발행이
     * Relay 에 의해 반드시 시도됨을 보장한다 (Transactional Outbox 패턴).</p>
     */
    @Transactional
    public Showcase3dModel saveModelAndSourceImages(Long showcaseId, List<String> imageUrls) {
        Showcase3dModel model = createAndSaveModel(showcaseId);
        saveSourceImages(model.getId(), imageUrls);
        modelGenerationEventPublisher.publishRequested(model.getId(), showcaseId);
        return model;
    }

    /**
     * 기존 3D 모델을 REQUESTED 상태로 재설정하거나, 없으면 새로 생성하고,
     * 소스 이미지 저장 및 Outbox 이벤트 기록까지 단일 트랜잭션으로 처리한다.
     */
    @Transactional
    public Showcase3dModel resetOrCreateModelAndSaveSourceImages(Long showcaseId, List<String> imageUrls) {
        Showcase3dModel model = showcase3dModelPort.findByShowcaseId(showcaseId)
                .map(existing -> showcase3dModelPort.save(
                        existing.resetRequest(GENERATION_PROVIDER)))
                .orElseGet(() -> createAndSaveModel(showcaseId));
        saveSourceImages(model.getId(), imageUrls);
        modelGenerationEventPublisher.publishRequested(model.getId(), showcaseId);
        return model;
    }

    private Showcase3dModel createAndSaveModel(Long showcaseId) {
        Showcase3dModel model = Showcase3dModel.request(showcaseId, GENERATION_PROVIDER);
        return showcase3dModelPort.save(model);
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
}
