package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.exception.DuplicateSortOrderException;
import com.gearshow.backend.showcase.application.exception.ImageNotBelongToShowcaseException;
import com.gearshow.backend.showcase.application.exception.ImageReorderMismatchException;
import com.gearshow.backend.showcase.application.exception.NotFoundShowcaseImageException;
import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.application.port.in.ManageShowcaseImageUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ShowcaseImagePort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 쇼케이스 이미지 관리 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class ManageShowcaseImageService implements ManageShowcaseImageUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final ImageStoragePort imageStoragePort;

    /**
     * S3 키 목록을 URL로 변환하여 이미지를 추가한다.
     * 이미지 업로드는 클라이언트가 Presigned URL로 S3에 직접 수행한 상태이다.
     */
    @Override
    @Transactional
    public List<Long> addImages(Long showcaseId, Long ownerId, List<String> imageKeys) {
        validateOwnershipAndGet(showcaseId, ownerId);

        int currentCount = showcaseImagePort.countByShowcaseId(showcaseId);
        List<ShowcaseImage> showcaseImages = new ArrayList<>();
        for (int i = 0; i < imageKeys.size(); i++) {
            String imageUrl = imageStoragePort.toUrl(imageKeys.get(i));
            showcaseImages.add(ShowcaseImage.create(
                    showcaseId, imageUrl,
                    currentCount + i + 1, false));
        }

        return showcaseImagePort.saveAll(showcaseImages).stream()
                .map(ShowcaseImage::getId)
                .toList();
    }

    @Override
    @Transactional
    public void deleteImage(Long showcaseId, Long imageId, Long ownerId) {
        Showcase showcase = validateOwnershipAndGet(showcaseId, ownerId);
        validateMinImageCount(showcaseId);

        ShowcaseImage image = showcaseImagePort.findById(imageId)
                .orElseThrow(NotFoundShowcaseImageException::new);
        validateImageBelongsToShowcase(image, showcaseId);

        showcaseImagePort.deleteById(imageId);

        // 대표 이미지가 삭제된 경우 다른 이미지를 대표로 승격
        if (image.isPrimary()) {
            showcaseImagePort.findByShowcaseId(showcaseId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        Showcase updated = showcase.changePrimaryImageUrl(next.getImageUrl());
                        showcasePort.save(updated);
                    });
        }

        // S3에서 이미지 파일 삭제
        imageStoragePort.delete(image.getImageUrl());
    }

    @Override
    @Transactional
    public void reorderImages(Long showcaseId, Long ownerId, List<ImageOrder> imageOrders) {
        Showcase showcase = validateOwnershipAndGet(showcaseId, ownerId);
        validateReorderInput(imageOrders);

        List<ShowcaseImage> existing = showcaseImagePort.findByShowcaseId(showcaseId);
        validateImageSetMatch(existing, imageOrders);

        List<ShowcaseImage> updated = buildReorderedImages(existing, imageOrders);
        showcaseImagePort.saveAll(updated);

        // 대표 이미지 URL 동기화
        syncPrimaryImageUrl(showcase, existing, imageOrders);
    }

    /**
     * 재정렬 입력값의 비즈니스 규칙을 검증한다.
     */
    private void validateReorderInput(List<ImageOrder> imageOrders) {
        validateSinglePrimaryImage(imageOrders);
        validateUniqueSortOrder(imageOrders);
    }

    /**
     * 기존 이미지에 새 정렬 순서를 적용한 이미지 목록을 생성한다.
     */
    private List<ShowcaseImage> buildReorderedImages(List<ShowcaseImage> existing,
                                                      List<ImageOrder> imageOrders) {
        Map<Long, ShowcaseImage> existingMap = existing.stream()
                .collect(Collectors.toMap(ShowcaseImage::getId, img -> img));

        List<ShowcaseImage> updated = new ArrayList<>();
        for (ImageOrder order : imageOrders) {
            ShowcaseImage img = existingMap.get(order.showcaseImageId());
            updated.add(ShowcaseImage.builder()
                    .id(img.getId())
                    .showcaseId(img.getShowcaseId())
                    .imageUrl(img.getImageUrl())
                    .sortOrder(order.sortOrder())
                    .primary(order.isPrimary())
                    .createdAt(img.getCreatedAt())
                    .build());
        }
        return updated;
    }

    /**
     * 소유권을 검증하고 쇼케이스를 반환한다.
     */
    private Showcase validateOwnershipAndGet(Long showcaseId, Long ownerId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        if (!showcase.getOwnerId().equals(ownerId)) {
            throw new NotOwnerShowcaseException();
        }
        return showcase;
    }

    /**
     * 이미지 재정렬 시 대표 이미지 URL을 쇼케이스에 동기화한다.
     */
    private void syncPrimaryImageUrl(Showcase showcase,
                                      List<ShowcaseImage> existing,
                                      List<ImageOrder> imageOrders) {
        Map<Long, ShowcaseImage> existingMap = existing.stream()
                .collect(Collectors.toMap(ShowcaseImage::getId, img -> img));

        imageOrders.stream()
                .filter(ImageOrder::isPrimary)
                .findFirst()
                .map(order -> existingMap.get(order.showcaseImageId()))
                .ifPresent(primaryImage -> {
                    Showcase updated = showcase.changePrimaryImageUrl(primaryImage.getImageUrl());
                    showcasePort.save(updated);
                });
    }

    /**
     * 이미지가 해당 쇼케이스에 속하는지 검증한다.
     */
    private void validateImageBelongsToShowcase(ShowcaseImage image, Long showcaseId) {
        if (!image.getShowcaseId().equals(showcaseId)) {
            throw new ImageNotBelongToShowcaseException();
        }
    }

    /**
     * 최소 1개의 이미지가 유지되어야 한다.
     */
    private void validateMinImageCount(Long showcaseId) {
        int count = showcaseImagePort.countByShowcaseId(showcaseId);
        if (count <= 1) {
            throw new InvalidShowcaseException();
        }
    }

    /**
     * 정확히 1개의 대표 이미지가 존재해야 한다.
     */
    private void validateSinglePrimaryImage(List<ImageOrder> imageOrders) {
        long primaryCount = imageOrders.stream()
                .filter(ImageOrder::isPrimary)
                .count();
        if (primaryCount != 1) {
            throw new InvalidShowcaseException();
        }
    }

    /**
     * 정렬 순서가 중복되지 않아야 한다.
     */
    private void validateUniqueSortOrder(List<ImageOrder> imageOrders) {
        Set<Integer> sortOrders = imageOrders.stream()
                .map(ImageOrder::sortOrder)
                .collect(Collectors.toSet());
        if (sortOrders.size() != imageOrders.size()) {
            throw new DuplicateSortOrderException();
        }
    }

    /**
     * 요청 이미지 목록과 실제 쇼케이스 이미지 목록이 정확히 일치하는지 검증한다.
     */
    private void validateImageSetMatch(List<ShowcaseImage> existing, List<ImageOrder> imageOrders) {
        Set<Long> existingIds = existing.stream()
                .map(ShowcaseImage::getId)
                .collect(Collectors.toSet());
        Set<Long> requestIds = imageOrders.stream()
                .map(ImageOrder::showcaseImageId)
                .collect(Collectors.toSet());

        // 요청 ID 중복 검증 (Set 크기와 원본 리스트 크기 비교)
        if (requestIds.size() != imageOrders.size()) {
            throw new ImageReorderMismatchException();
        }

        if (!existingIds.equals(requestIds)) {
            throw new ImageReorderMismatchException();
        }
    }
}
