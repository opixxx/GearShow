package com.gearshow.backend.showcase.adapter.in.web.dto;

import java.util.List;

/**
 * 쇼케이스 이미지 추가 응답 DTO.
 *
 * @param addedImageIds 추가된 이미지 ID 목록
 */
public record AddImagesResponse(List<Long> addedImageIds) {
}
