package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.ReorderImagesRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.UploadFileMapper;
import com.gearshow.backend.showcase.application.port.in.ManageShowcaseImageUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 쇼케이스 이미지 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/showcases/{showcaseId}/images")
@RequiredArgsConstructor
public class ShowcaseImageController {

    private final ManageShowcaseImageUseCase manageShowcaseImageUseCase;

    /**
     * 이미지를 추가한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, List<Long>>>> addImages(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @RequestParam("images") List<MultipartFile> images) {

        Long ownerId = (Long) authentication.getPrincipal();
        List<Long> addedIds = manageShowcaseImageUseCase.addImages(
                showcaseId, ownerId, UploadFileMapper.toUploadFiles(images));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(201, "이미지 추가 성공",
                        Map.of("addedImageIds", addedIds)));
    }

    /**
     * 이미지를 삭제한다.
     */
    @DeleteMapping("/{showcaseImageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @PathVariable Long showcaseImageId) {

        Long ownerId = (Long) authentication.getPrincipal();
        manageShowcaseImageUseCase.deleteImage(showcaseId, showcaseImageId, ownerId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "이미지 삭제 성공"));
    }

    /**
     * 이미지 정렬 순서를 변경한다.
     */
    @PatchMapping("/order")
    public ResponseEntity<ApiResponse<Void>> reorderImages(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @Valid @RequestBody ReorderImagesRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();
        List<ManageShowcaseImageUseCase.ImageOrder> orders = request.imageOrders().stream()
                .map(o -> new ManageShowcaseImageUseCase.ImageOrder(
                        o.showcaseImageId(), o.sortOrder(), o.isPrimary()))
                .toList();
        manageShowcaseImageUseCase.reorderImages(showcaseId, ownerId, orders);

        return ResponseEntity.ok(
                ApiResponse.of(200, "이미지 정렬 순서 변경 성공"));
    }
}
