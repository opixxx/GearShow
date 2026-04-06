package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.GeneratePresignedUrlRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.GeneratePresignedUrlResponse;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Presigned URL 발급 API 컨트롤러.
 * 클라이언트가 S3에 직접 업로드하기 전에 서버로부터 PUT용 서명 URL을 발급받는다.
 */
@RestController
@RequiredArgsConstructor
public class ShowcasePresignedUrlController {

    private final GeneratePresignedUrlUseCase generatePresignedUrlUseCase;

    /**
     * 쇼케이스 등록용 Presigned URL을 발급한다.
     * SHOWCASE_IMAGE, MODEL_SOURCE 유형 모두 허용된다.
     */
    @PostMapping("/api/v1/showcases/upload-urls")
    public ResponseEntity<ApiResponse<List<GeneratePresignedUrlResponse>>> generateUploadUrls(
            @Valid @RequestBody GeneratePresignedUrlRequest request) {

        List<GeneratePresignedUrlResponse> result = generatePresignedUrlUseCase
                .generate(request.toFileUploadRequests())
                .stream()
                .map(GeneratePresignedUrlResponse::from)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.of(200, "Presigned URL 발급 성공", result));
    }

    /**
     * 기존 쇼케이스 이미지 추가용 Presigned URL을 발급한다.
     */
    @PostMapping("/api/v1/showcases/{showcaseId}/images/upload-urls")
    public ResponseEntity<ApiResponse<List<GeneratePresignedUrlResponse>>> generateImagesUploadUrls(
            @PathVariable Long showcaseId,
            @Valid @RequestBody GeneratePresignedUrlRequest request) {

        List<GeneratePresignedUrlResponse> result = generatePresignedUrlUseCase
                .generateForShowcase(showcaseId, request.toFileUploadRequests())
                .stream()
                .map(GeneratePresignedUrlResponse::from)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.of(200, "Presigned URL 발급 성공", result));
    }
}
