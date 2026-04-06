package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.UploadFileType;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase.FileUploadRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Presigned URL 발급 요청 DTO.
 *
 * @param files 업로드할 파일 정보 목록 (최소 1개)
 */
public record GeneratePresignedUrlRequest(
        @NotEmpty(message = "파일 목록은 비어있을 수 없습니다")
        @Valid
        List<FileInfo> files
) {

    /**
     * 개별 파일 정보.
     *
     * @param contentType 파일 Content-Type (예: "image/jpeg")
     * @param filename    원본 파일명 (확장자 추출에 사용)
     * @param type        업로드 파일 유형
     */
    public record FileInfo(
            @NotBlank(message = "Content-Type은 필수입니다")
            String contentType,

            @NotBlank(message = "파일명은 필수입니다")
            String filename,

            @NotNull(message = "파일 유형은 필수입니다")
            UploadFileType type
    ) {
        /**
         * 요청을 유스케이스 입력 객체로 변환한다.
         */
        public FileUploadRequest toFileUploadRequest() {
            return new FileUploadRequest(contentType, filename, type);
        }
    }

    /**
     * 요청 목록을 유스케이스 입력 목록으로 변환한다.
     */
    public List<FileUploadRequest> toFileUploadRequests() {
        return files.stream()
                .map(FileInfo::toFileUploadRequest)
                .toList();
    }
}
