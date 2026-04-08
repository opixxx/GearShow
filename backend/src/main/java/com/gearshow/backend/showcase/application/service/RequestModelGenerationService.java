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
 * <p>DB 저장만 담당한다. 외부 I/O(S3, Kafka)는 {@link RequestModelGenerationFacade}에서 수행한다.</p>
 */
@Service
@RequiredArgsConstructor
public class RequestModelGenerationService {

    private static final String GENERATION_PROVIDER = "fake-tripo";
    private static final AngleType[] ANGLE_TYPES = AngleType.values();

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelSourceImagePort modelSourceImagePort;

    /**
     * 신규 3D 모델과 소스 이미지를 저장한다.
     */
    @Transactional
    public Showcase3dModel saveModelAndSourceImages(Long showcaseId, List<String> imageUrls) {
        Showcase3dModel model = createAndSaveModel(showcaseId);
        saveSourceImages(model.getId(), imageUrls);
        return model;
    }

    /**
     * 기존 3D 모델을 REQUESTED 상태로 재설정하거나, 없으면 새로 생성하고 소스 이미지를 저장한다.
     */
    @Transactional
    public Showcase3dModel resetOrCreateModelAndSaveSourceImages(Long showcaseId, List<String> imageUrls) {
        Showcase3dModel model = showcase3dModelPort.findByShowcaseId(showcaseId)
                .map(existing -> showcase3dModelPort.save(
                        existing.resetRequest(GENERATION_PROVIDER)))
                .orElseGet(() -> createAndSaveModel(showcaseId));
        saveSourceImages(model.getId(), imageUrls);
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
