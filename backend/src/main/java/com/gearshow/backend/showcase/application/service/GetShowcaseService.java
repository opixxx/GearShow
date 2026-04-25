package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseSpecPort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 쇼케이스 상세 조회 유스케이스 (ADR-010 Q4-(1) 반영).
 *
 * <p>3D 모델 상태는 완성품 존재 여부 + {@code model_generation_workflow} 최신 attempt 의
 * {@code current_step} 으로부터 {@link ShowcaseModelStatusResolver} 를 통해 유도한다.</p>
 */
@Service
@RequiredArgsConstructor
public class GetShowcaseService implements GetShowcaseUseCase {

    private static final Set<ShowcaseStatus> PUBLIC_VISIBLE_STATUSES =
            Set.of(ShowcaseStatus.ACTIVE, ShowcaseStatus.SOLD);

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final Showcase3dModelPort showcase3dModelPort;
    private final ShowcaseSpecPort showcaseSpecPort;
    private final ModelGenerationWorkflowPort modelGenerationWorkflowPort;

    @Override
    @Transactional(readOnly = true)
    public ShowcaseDetailResult getShowcase(Long showcaseId) {
        Showcase showcase = findPublicShowcase(showcaseId);
        List<ShowcaseImage> images = showcaseImagePort.findByShowcaseId(showcaseId);
        Showcase3dModel model3d = showcase3dModelPort.findByShowcaseId(showcaseId)
                .orElse(null);
        ShowcaseSpec spec = showcaseSpecPort.findByShowcaseId(showcaseId)
                .orElse(null);

        Optional<WorkflowSnapshot> latestWorkflow =
                modelGenerationWorkflowPort.findLatestSnapshotByShowcaseId(showcaseId);
        ShowcaseModelStatus modelStatus = ShowcaseModelStatusResolver.resolve(
                model3d != null,
                latestWorkflow.map(WorkflowSnapshot::currentStep).orElse(null));

        return ShowcaseDetailResult.of(showcase, images, model3d, modelStatus, spec);
    }

    private Showcase findPublicShowcase(Long showcaseId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);

        if (!PUBLIC_VISIBLE_STATUSES.contains(showcase.getStatus())) {
            throw new NotFoundShowcaseException();
        }

        return showcase;
    }
}
