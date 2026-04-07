package com.gearshow.backend.showcase.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.application.exception.ShowcaseSpecSerializationException;
import com.gearshow.backend.showcase.application.port.out.ShowcaseSpecPort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import com.gearshow.backend.showcase.domain.vo.SpecType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ShowcaseSpecPort showcaseSpecPort;
    private final ObjectMapper objectMapper;

    /**
     * 쇼케이스, 이미지, 스펙을 DB에 저장한다.
     */
    @Transactional
    public Showcase saveShowcaseWithSpec(CreateShowcaseCommand command, List<String> imageUrls) {
        // 대표 이미지 URL을 미리 결정하여 Showcase에 함께 저장
        String primaryImageUrl = imageUrls.get(command.primaryImageIndex());
        Showcase showcase = createShowcase(command, primaryImageUrl);
        Showcase saved = showcasePort.save(showcase);
        saveImages(saved.getId(), imageUrls, command.primaryImageIndex());
        saveSpec(saved.getId(), command.category(), command);
        return saved;
    }

    private Showcase createShowcase(CreateShowcaseCommand command, String primaryImageUrl) {
        return Showcase.create(
                command.ownerId(), command.catalogItemId(),
                command.category(), command.brand(), command.modelCode(),
                command.title(), command.description(),
                command.userSize(), command.conditionGrade(),
                command.wearCount(), command.isForSale(),
                primaryImageUrl);
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
     * 카테고리에 따라 쇼케이스 스펙을 JSON으로 변환하여 저장한다.
     */
    private void saveSpec(Long showcaseId, Category category, CreateShowcaseCommand command) {
        try {
            if (category == Category.BOOTS && command.bootsSpec() != null) {
                String specData = objectMapper.writeValueAsString(command.bootsSpec());
                showcaseSpecPort.save(ShowcaseSpec.create(showcaseId, SpecType.BOOTS, specData));
            } else if (category == Category.UNIFORM && command.uniformSpec() != null) {
                String specData = objectMapper.writeValueAsString(command.uniformSpec());
                showcaseSpecPort.save(ShowcaseSpec.create(showcaseId, SpecType.UNIFORM, specData));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ShowcaseSpecSerializationException();
        }
    }
}
