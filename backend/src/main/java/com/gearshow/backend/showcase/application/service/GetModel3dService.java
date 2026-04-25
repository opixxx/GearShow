package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.port.in.GetModel3dUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 3D 모델 상세 조회 유스케이스 (ADR-010 프로세스 분리 반영).
 *
 * <p>완성품이 존재하면 그 스냅샷을, 아직이면 {@code model_generation_workflow} 최신 attempt 에서
 * 상태를 유도한다. 둘 다 없으면 {@link NotFoundShowcaseException}.</p>
 */
@Service
@RequiredArgsConstructor
public class GetModel3dService implements GetModel3dUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelSourceImagePort modelSourceImagePort;
    private final ModelGenerationWorkflowPort modelGenerationWorkflowPort;

    @Override
    @Transactional(readOnly = true)
    public Model3dDetailResult getModel3d(Long showcaseId) {
        Optional<Showcase3dModel> completedModel = showcase3dModelPort.findByShowcaseId(showcaseId);
        Optional<WorkflowSnapshot> latestWorkflow =
                modelGenerationWorkflowPort.findLatestSnapshotByShowcaseId(showcaseId);

        if (completedModel.isEmpty() && latestWorkflow.isEmpty()) {
            throw new NotFoundShowcaseException();
        }

        ShowcaseModelStatus status = ShowcaseModelStatusResolver.resolve(
                completedModel.isPresent(),
                latestWorkflow.map(WorkflowSnapshot::currentStep).orElse(null));

        int sourceImageCount = modelSourceImagePort.countByShowcaseId(showcaseId);

        return new Model3dDetailResult(
                completedModel.map(Showcase3dModel::getId).orElse(null),
                completedModel.map(Showcase3dModel::getModelFileUrl).orElse(null),
                completedModel.map(Showcase3dModel::getPreviewImageUrl).orElse(null),
                status,
                sourceImageCount,
                latestWorkflow.map(WorkflowSnapshot::startedAt).orElse(null),
                completedModel.map(Showcase3dModel::getGeneratedAt).orElse(null),
                latestWorkflow.map(WorkflowSnapshot::failureMessage).orElse(null)
        );
    }
}
