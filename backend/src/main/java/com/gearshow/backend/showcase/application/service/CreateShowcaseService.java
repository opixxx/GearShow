package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.out.ShowcaseBootsSpecPort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseUniformSpecPort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseBootsSpec;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseUniformSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 쇼케이스 DB 저장 서비스.
 *
 * <p>쇼케이스, 이미지, 스펙을 하나의 트랜잭션으로 저장한다.
 * 외부 I/O(S3, 3D 모델)는 {@link CreateShowcaseFacade}가 트랜잭션 밖에서 처리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CreateShowcaseService {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final ShowcaseBootsSpecPort showcaseBootsSpecPort;
    private final ShowcaseUniformSpecPort showcaseUniformSpecPort;

    /**
     * 쇼케이스, 이미지, 스펙을 DB에 저장한다.
     */
    @Transactional
    public Showcase saveShowcaseWithSpec(CreateShowcaseCommand command, List<String> imageUrls) {
        Showcase showcase = createShowcase(command);
        Showcase saved = showcasePort.save(showcase);
        saveImages(saved.getId(), imageUrls, command.primaryImageIndex());
        saveSpec(saved.getId(), command.category(), command);
        return saved;
    }

    private Showcase createShowcase(CreateShowcaseCommand command) {
        return Showcase.create(
                command.ownerId(), command.catalogItemId(),
                command.category(), command.brand(), command.modelCode(),
                command.title(), command.description(),
                command.userSize(), command.conditionGrade(),
                command.wearCount(), command.isForSale());
    }

    private void saveImages(Long showcaseId, List<String> imageUrls, int primaryImageIndex) {
        List<ShowcaseImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(ShowcaseImage.create(
                    showcaseId, imageUrls.get(i),
                    i + 1, i == primaryImageIndex));
        }
        showcaseImagePort.saveAll(images);
    }

    /**
     * 카테고리에 따라 쇼케이스 스펙을 저장한다.
     */
    private void saveSpec(Long showcaseId, Category category, CreateShowcaseCommand command) {
        if (category == Category.BOOTS && command.bootsSpec() != null) {
            saveBootsSpec(showcaseId, command.bootsSpec());
        } else if (category == Category.UNIFORM && command.uniformSpec() != null) {
            saveUniformSpec(showcaseId, command.uniformSpec());
        }
    }

    private void saveBootsSpec(Long showcaseId, CreateShowcaseCommand.BootsSpecCommand spec) {
        Instant now = Instant.now();
        ShowcaseBootsSpec bootsSpec = ShowcaseBootsSpec.builder()
                .showcaseId(showcaseId)
                .studType(spec.studType())
                .siloName(spec.siloName())
                .releaseYear(spec.releaseYear())
                .surfaceType(spec.surfaceType())
                .extraSpecJson(spec.extraSpecJson())
                .createdAt(now)
                .updatedAt(now)
                .build();
        showcaseBootsSpecPort.save(bootsSpec);
    }

    private void saveUniformSpec(Long showcaseId, CreateShowcaseCommand.UniformSpecCommand spec) {
        Instant now = Instant.now();
        ShowcaseUniformSpec uniformSpec = ShowcaseUniformSpec.builder()
                .showcaseId(showcaseId)
                .clubName(spec.clubName())
                .season(spec.season())
                .league(spec.league())
                .kitType(spec.kitType())
                .extraSpecJson(spec.extraSpecJson())
                .createdAt(now)
                .updatedAt(now)
                .build();
        showcaseUniformSpecPort.save(uniformSpec);
    }
}
