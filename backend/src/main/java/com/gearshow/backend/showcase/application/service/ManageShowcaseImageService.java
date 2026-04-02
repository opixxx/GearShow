package com.gearshow.backend.showcase.application.service;

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
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 쇼케이스 이미지 관리 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class ManageShowcaseImageService implements ManageShowcaseImageUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseImagePort showcaseImagePort;
    private final ImageStoragePort imageStoragePort;

    @Override
    @Transactional
    public List<Long> addImages(Long showcaseId, Long ownerId, List<MultipartFile> images) {
        validateOwnership(showcaseId, ownerId);

        // S3 업로드 후 이미지 저장
        List<String> imageUrls = imageStoragePort.uploadAll(
                "showcases/" + showcaseId, images);

        int currentCount = showcaseImagePort.countByShowcaseId(showcaseId);
        List<ShowcaseImage> showcaseImages = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            showcaseImages.add(ShowcaseImage.create(
                    showcaseId, imageUrls.get(i),
                    currentCount + i + 1, false));
        }

        return showcaseImagePort.saveAll(showcaseImages).stream()
                .map(ShowcaseImage::getId)
                .toList();
    }

    @Override
    @Transactional
    public void deleteImage(Long showcaseId, Long imageId, Long ownerId) {
        validateOwnership(showcaseId, ownerId);
        validateMinImageCount(showcaseId);

        // DB에서 이미지 조회 후 삭제
        ShowcaseImage image = showcaseImagePort.findById(imageId)
                .orElseThrow(InvalidShowcaseException::new);

        showcaseImagePort.deleteById(imageId);

        // S3에서 이미지 파일 삭제
        imageStoragePort.delete(image.getImageUrl());
    }

    @Override
    @Transactional
    public void reorderImages(Long showcaseId, Long ownerId, List<ImageOrder> imageOrders) {
        validateOwnership(showcaseId, ownerId);
        validateSinglePrimaryImage(imageOrders);

        List<ShowcaseImage> existing = showcaseImagePort.findByShowcaseId(showcaseId);
        List<ShowcaseImage> updated = new ArrayList<>();

        for (ImageOrder order : imageOrders) {
            existing.stream()
                    .filter(img -> img.getId().equals(order.showcaseImageId()))
                    .findFirst()
                    .ifPresent(img -> updated.add(ShowcaseImage.builder()
                            .id(img.getId())
                            .showcaseId(img.getShowcaseId())
                            .imageUrl(img.getImageUrl())
                            .sortOrder(order.sortOrder())
                            .primary(order.isPrimary())
                            .createdAt(img.getCreatedAt())
                            .build()));
        }

        showcaseImagePort.saveAll(updated);
    }

    private void validateOwnership(Long showcaseId, Long ownerId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        if (!showcase.getOwnerId().equals(ownerId)) {
            throw new NotOwnerShowcaseException();
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
}
