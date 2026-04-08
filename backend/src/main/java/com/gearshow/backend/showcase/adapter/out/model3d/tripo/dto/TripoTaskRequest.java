package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import java.util.List;

/**
 * Tripo multiview_to_model Task 생성 요청.
 */
public record TripoTaskRequest(
        String type,
        String model_version,
        List<FileRef> files
) {
    /**
     * 이미지 파일 참조.
     * file_token으로 업로드된 이미지를 참조한다.
     */
    public record FileRef(
            String type,
            String file_token
    ) {}

    /**
     * Multiview 3D 생성 요청을 생성한다.
     *
     * @param modelVersion 모델 버전 (예: v2.5-20250123)
     * @param imageTokens  이미지 토큰 목록 [앞, 뒤, 좌, 우]
     * @return Task 생성 요청
     */
    public static TripoTaskRequest multiview(String modelVersion, List<String> imageTokens) {
        List<FileRef> files = imageTokens.stream()
                .map(token -> new FileRef("jpg", token))
                .toList();
        return new TripoTaskRequest("multiview_to_model", modelVersion, files);
    }
}
