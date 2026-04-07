package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.UploadFileType;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase.PresignedUrlResult;

/**
 * Presigned URL 발급 응답 DTO.
 *
 * @param presignedUrl PUT 업로드용 Presigned URL (만료: 10분)
 * @param s3Key        S3 객체 키 — 쇼케이스 등록 요청 시 서버에 전달
 * @param type         업로드 파일 유형
 */
public record GeneratePresignedUrlResponse(
        String presignedUrl,
        String s3Key,
        UploadFileType type
) {

    public static GeneratePresignedUrlResponse from(PresignedUrlResult result) {
        return new GeneratePresignedUrlResponse(
                result.presignedUrl(),
                result.s3Key(),
                result.type());
    }
}
