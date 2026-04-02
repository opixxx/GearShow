package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 쇼케이스 상세 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class GetShowcaseService implements GetShowcaseUseCase {

    /** 공개 상세 조회에서 허용되는 상태 (ACTIVE, SOLD만 노출) */
    private static final Set<ShowcaseStatus> PUBLIC_VISIBLE_STATUSES =
            Set.of(ShowcaseStatus.ACTIVE, ShowcaseStatus.SOLD);

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final Showcase3dModelPort showcase3dModelPort;

    @Override
    @Transactional(readOnly = true)
    public ShowcaseDetailResult getShowcase(Long showcaseId) {
        Showcase showcase = findPublicShowcase(showcaseId);
        List<ShowcaseImage> images = showcaseImagePort.findByShowcaseId(showcaseId);
        Showcase3dModel model3d = showcase3dModelPort.findByShowcaseId(showcaseId)
                .orElse(null);

        return ShowcaseDetailResult.of(showcase, images, model3d);
    }

    /**
     * 공개 조회 가능한 쇼케이스를 조회한다.
     * HIDDEN, DELETED 상태는 조회할 수 없다.
     */
    private Showcase findPublicShowcase(Long showcaseId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);

        if (!PUBLIC_VISIBLE_STATUSES.contains(showcase.getStatus())) {
            throw new NotFoundShowcaseException();
        }

        return showcase;
    }
}
