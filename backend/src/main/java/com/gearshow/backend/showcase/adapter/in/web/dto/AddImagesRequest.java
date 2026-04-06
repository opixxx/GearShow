package com.gearshow.backend.showcase.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 쇼케이스 이미지 추가 요청 DTO.
 * 클라이언트가 Presigned URL로 S3에 직접 업로드한 후 S3 키 목록을 전달한다.
 *
 * @param imageKeys 추가할 이미지 S3 키 목록 (최소 1개)
 */
public record AddImagesRequest(
        @NotEmpty(message = "이미지 키 목록은 비어있을 수 없습니다")
        List<String> imageKeys
) {}
