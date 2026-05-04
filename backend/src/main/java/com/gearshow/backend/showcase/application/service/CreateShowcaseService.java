package com.gearshow.backend.showcase.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseOutcome;
import com.gearshow.backend.showcase.application.exception.ShowcaseSpecSerializationException;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseSpecPort;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import com.gearshow.backend.showcase.domain.vo.ContentHash;
import com.gearshow.backend.showcase.domain.vo.SpecType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 DB 저장 서비스.
 *
 * <p>쇼케이스, 이미지, 스펙을 하나의 트랜잭션으로 저장한다.
 * 외부 I/O(S3, 3D 모델)는 {@link CreateShowcaseFacade}가 트랜잭션 밖에서 처리한다.</p>
 *
 * <p><b>content_hash fallback 멱등성 (ADR-011 ②)</b>: {@code command.contentHash} 가 제공되면
 * 같은 소유자가 {@link #CONTENT_HASH_DEDUP_WINDOW} 창 내에 동일 해시로 등록한 기존 쇼케이스를 찾아
 * 그대로 반환한다. 이미지·스펙은 재저장하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateShowcaseService {

    /** content_hash 기반 중복 등록 감지 시간 창. 10분 밖 재업로드는 정상 요청으로 허용. */
    private static final Duration CONTENT_HASH_DEDUP_WINDOW = Duration.ofMinutes(10);

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final ShowcaseSpecPort showcaseSpecPort;
    private final SearchTextSynchronizer searchTextSynchronizer;
    private final ObjectMapper objectMapper;

    /**
     * 쇼케이스, 이미지, 스펙을 DB에 저장한다. contentHash 기반 10분 창 중복 감지 시
     * 기존 쇼케이스를 {@link CreateShowcaseOutcome.Deduped} 로 반환하여 호출자가 후속 외부 I/O 를
     * 스킵하도록 한다 (ADR-011 ②).
     */
    @Transactional
    public CreateShowcaseOutcome saveShowcaseWithSpec(CreateShowcaseCommand command, List<String> imageUrls) {
        Optional<Showcase> existing = findRecentDuplicate(command);
        if (existing.isPresent()) {
            log.info("content_hash 기반 중복 등록 감지 — 기존 쇼케이스 반환. ownerId={}, showcaseId={}",
                    command.ownerId(), existing.get().getId());
            // ADR-018 §D4: dedup hit 시 기존 search_text 유지 — 재합성 안 함.
            // 중복 등록을 update 로 변질시키지 않는다.
            return new CreateShowcaseOutcome.Deduped(existing.get());
        }

        String primaryImageUrl = imageUrls.get(command.primaryImageIndex());

        Showcase showcase = Showcase.create(
                command.ownerId(), command.catalogItemId(),
                command.category(), command.brand(), command.modelCode(),
                command.title(), command.description(),
                command.userSize(), command.conditionGrade(),
                command.wearCount(), command.isForSale(),
                primaryImageUrl,
                command.contentHash()
        );
        // ADR-018: 등록 시점 1회 search_text 합성 후 영속.
        Showcase withSearchText = searchTextSynchronizer.synchronize(showcase);
        Showcase saved = showcasePort.save(withSearchText);

        saveImages(saved.getId(), imageUrls, command.primaryImageIndex());
        saveSpec(saved.getId(), command.category(), command);

        return new CreateShowcaseOutcome.Created(saved);
    }

    private Optional<Showcase> findRecentDuplicate(CreateShowcaseCommand command) {
        ContentHash contentHash = command.contentHash();
        if (contentHash == null) {
            return Optional.empty();
        }
        Instant threshold = Instant.now().minus(CONTENT_HASH_DEDUP_WINDOW);
        return showcasePort.findRecentByOwnerAndContentHash(
                command.ownerId(), contentHash, threshold);
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
