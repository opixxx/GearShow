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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쇼케이스 상세 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class GetShowcaseService implements GetShowcaseUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final Showcase3dModelPort showcase3dModelPort;

    @Override
    @Transactional(readOnly = true)
    public ShowcaseDetailResult getShowcase(Long showcaseId) {
        Showcase showcase = findShowcase(showcaseId);
        List<ShowcaseImage> images = showcaseImagePort.findByShowcaseId(showcaseId);
        Showcase3dModel model3d = showcase3dModelPort.findByShowcaseId(showcaseId)
                .orElse(null);

        return ShowcaseDetailResult.of(showcase, images, model3d);
    }

    private Showcase findShowcase(Long showcaseId) {
        return showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
    }
}
