package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;
import com.gearshow.backend.showcase.application.port.in.GetModel3dUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3D 모델 상태 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class GetModel3dService implements GetModel3dUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelSourceImagePort modelSourceImagePort;

    @Override
    @Transactional(readOnly = true)
    public Model3dDetailResult getModel3d(Long showcaseId) {
        Showcase3dModel model = showcase3dModelPort.findByShowcaseId(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);

        int sourceImageCount = modelSourceImagePort.countByShowcase3dModelId(model.getId());
        return Model3dDetailResult.of(model, sourceImageCount);
    }
}
