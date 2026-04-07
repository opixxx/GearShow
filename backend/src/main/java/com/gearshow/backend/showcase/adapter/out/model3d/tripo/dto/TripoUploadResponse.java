package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

/**
 * Tripo 이미지 업로드 응답.
 */
public record TripoUploadResponse(
        int code,
        TripoUploadData data
) {
    public record TripoUploadData(String image_token) {}
}
