package com.gearshow.backend.showcase.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 3D 모델 생성 재요청 DTO.
 * 클라이언트가 Presigned URL로 S3에 직접 업로드한 후 S3 키 목록을 전달한다.
 *
 * @param modelSourceImageKeys 소스 이미지 S3 키 목록 (최소 4개, 앞/뒤/좌/우)
 */
public record RequestModelGenerationRequest(
        @NotEmpty(message = "소스 이미지 키 목록은 비어있을 수 없습니다")
        @Size(min = 4, message = "3D 모델 생성에는 최소 4장의 소스 이미지가 필요합니다")
        List<String> modelSourceImageKeys
) {}
