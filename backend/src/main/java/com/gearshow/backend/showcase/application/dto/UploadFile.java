package com.gearshow.backend.showcase.application.dto;

import java.io.InputStream;
import java.util.Objects;

/**
 * 파일 업로드 추상화 객체.
 * Application 계층에서 Spring MultipartFile에 의존하지 않기 위한 중간 모델.
 *
 * @param inputStream  파일 입력 스트림 (필수)
 * @param contentType  콘텐츠 타입 (예: "image/jpeg")
 * @param size         파일 크기 (바이트, 0 이상)
 * @param originalFilename 원본 파일명
 */
public record UploadFile(
        InputStream inputStream,
        String contentType,
        long size,
        String originalFilename
) {

    /**
     * 불변식을 검증하는 컴팩트 생성자.
     */
    public UploadFile {
        Objects.requireNonNull(inputStream, "inputStream은 필수입니다");
        if (size < 0) {
            throw new IllegalArgumentException("파일 크기는 0 이상이어야 합니다");
        }
        contentType = (contentType != null) ? contentType : "";
        originalFilename = (originalFilename != null) ? originalFilename : "";
    }
}
