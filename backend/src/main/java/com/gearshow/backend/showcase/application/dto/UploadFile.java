package com.gearshow.backend.showcase.application.dto;

import java.io.InputStream;

/**
 * 파일 업로드 추상화 객체.
 * Application 계층에서 Spring MultipartFile에 의존하지 않기 위한 중간 모델.
 *
 * @param inputStream  파일 입력 스트림
 * @param contentType  콘텐츠 타입 (예: "image/jpeg")
 * @param size         파일 크기 (바이트)
 * @param originalFilename 원본 파일명
 */
public record UploadFile(
        InputStream inputStream,
        String contentType,
        long size,
        String originalFilename
) {}
