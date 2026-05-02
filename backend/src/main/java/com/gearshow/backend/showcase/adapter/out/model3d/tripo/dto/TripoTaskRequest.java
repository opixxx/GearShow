package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Tripo {@code multiview_to_model} Task 생성 요청.
 *
 * <p><b>네이밍 주의</b>: record 컴포넌트는 의도적으로 snake_case 다. Jackson default 네이밍
 * 전략에서 컴포넌트명이 그대로 JSON 키가 되어 Tripo wire 포맷 ({@code "model_version"},
 * {@code "geometry_quality"} 등) 과 1:1 매핑된다. 컴포넌트명을 camelCase 로 바꾸면 Tripo
 * API 호출이 즉시 깨진다.</p>
 *
 * <p>품질 옵션 ({@code texture_quality}, {@code face_limit}, {@code orientation},
 * {@code geometry_quality}) 은 모두 nullable. {@link JsonInclude} 로 null 필드는 직렬화 시
 * 제외되어, 옵션 미설정 시 Tripo 측 default 가 적용된다 (테스트 / dev 환경 호환).</p>
 *
 * <p>옵션값 출처는 {@code TripoApiProperties} (외부화) — 본 record 는 데이터 holder 만 담당하며
 * 검증 로직은 properties 측 책임.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripoTaskRequest(
        String type,
        String model_version,
        List<FileRef> files,
        String texture_quality,
        Integer face_limit,
        String orientation,
        String geometry_quality
) {
    /**
     * 이미지 파일 참조. {@code file_token} 으로 업로드된 이미지를 참조한다.
     */
    public record FileRef(
            String type,
            String file_token
    ) {}

    /**
     * Multiview 3D 생성 요청에 함께 보낼 품질 옵션. 모든 필드 nullable — null 이면 Tripo default.
     *
     * @param textureQuality  텍스처 품질 ({@code standard} / {@code detailed})
     * @param faceLimit       메시 폴리곤 수 상한 (예: 200_000). null 이면 Tripo {@code adaptive}
     * @param orientation     이미지 정렬 ({@code default} / {@code align_image})
     * @param geometryQuality 메시 형상 품질 ({@code standard} / {@code detailed} = v3.0+ Ultra Mode)
     */
    public record MultiviewOptions(
            String textureQuality,
            Integer faceLimit,
            String orientation,
            String geometryQuality
    ) {
        /** 모두 null 인 옵션 — Tripo default 사용을 의도하는 호출자용. */
        public static MultiviewOptions defaults() {
            return new MultiviewOptions(null, null, null, null);
        }
    }

    /**
     * Multiview 3D 생성 요청을 생성한다.
     *
     * @param modelVersion 모델 버전 (예: {@code v3.0-20250812})
     * @param imageTokens  이미지 토큰 목록 [앞, 좌, 뒤, 우]
     * @param options      품질 옵션. {@code null} 이면 {@link MultiviewOptions#defaults()} 적용
     * @return Task 생성 요청
     */
    public static TripoTaskRequest multiview(String modelVersion,
                                             List<String> imageTokens,
                                             MultiviewOptions options) {
        MultiviewOptions opts = options != null ? options : MultiviewOptions.defaults();
        List<FileRef> files = imageTokens.stream()
                .map(token -> new FileRef("jpg", token))
                .toList();
        return new TripoTaskRequest(
                "multiview_to_model",
                modelVersion,
                files,
                opts.textureQuality(),
                opts.faceLimit(),
                opts.orientation(),
                opts.geometryQuality());
    }
}
