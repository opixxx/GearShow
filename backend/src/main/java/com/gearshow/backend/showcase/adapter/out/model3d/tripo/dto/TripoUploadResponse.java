package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tripo 이미지 업로드 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripoUploadResponse(
        int code,
        TripoUploadData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripoUploadData(String image_token) {}
}
