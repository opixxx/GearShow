package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.UploadFileType;

import java.util.List;

/**
 * Presigned URL 생성 유스케이스.
 * 클라이언트가 S3에 직접 업로드하기 전에 서버로부터 PUT용 서명 URL을 발급받는다.
 */
public interface GeneratePresignedUrlUseCase {

    /**
     * 쇼케이스 등록용 Presigned URL 목록을 생성한다.
     * SHOWCASE_IMAGE, MODEL_SOURCE 유형 모두 허용된다.
     *
     * @param files 업로드할 파일 정보 목록
     * @return Presigned URL 결과 목록 (요청 순서와 동일)
     */
    List<PresignedUrlResult> generate(List<FileUploadRequest> files);

    /**
     * 기존 쇼케이스 이미지 추가용 Presigned URL 목록을 생성한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param files      업로드할 파일 정보 목록
     * @return Presigned URL 결과 목록 (요청 순서와 동일)
     */
    List<PresignedUrlResult> generateForShowcase(Long showcaseId, List<FileUploadRequest> files);

    /**
     * 파일 업로드 요청 정보.
     *
     * @param contentType 파일 Content-Type (예: "image/jpeg")
     * @param filename    원본 파일명 — 확장자 추출 목적으로만 사용
     * @param type        업로드 파일 유형
     */
    record FileUploadRequest(String contentType, String filename, UploadFileType type) {}

    /**
     * Presigned URL 생성 결과.
     *
     * @param presignedUrl PUT 업로드용 Presigned URL (만료: 10분)
     * @param s3Key        S3 객체 키 — 쇼케이스 등록 요청 시 서버에 전달
     * @param type         업로드 파일 유형
     */
    record PresignedUrlResult(String presignedUrl, String s3Key, UploadFileType type) {}
}
