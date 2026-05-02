package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tripo HTTP API 호출 관련 설정.
 *
 * <p>P1-G aftermath 에서 {@code TripoPollingProperties} 의 {@code semaphoreAcquireTimeoutMs} 를
 * 분리해 가져왔다. Tripo 자체 통신 동작 (세마포어, 추후 Circuit Breaker 추가 시 별도 옵션) +
 * multiview 품질 옵션을 다룬다.</p>
 *
 * <p>품질 옵션은 모두 {@code TripoTaskRequest.MultiviewOptions} 로 전달되어 Tripo POST 요청에
 * 직렬화된다. 검증은 본 properties 가 책임 — DTO 레이어는 데이터 holder 만 담당한다.</p>
 *
 * @param semaphoreAcquireTimeoutMs Tripo semaphore permit 획득 대기 상한(ms).
 *                                  초과 시 {@code TripoSemaphoreTimeoutException}.
 * @param textureQuality            multiview 텍스처 품질. Tripo 옵션명 {@code texture_quality}.
 *                                  default {@code detailed} = 4K 텍스처 (유니폼 디테일 향상).
 * @param faceLimit                 메시 폴리곤 수 상한. Tripo 옵션명 {@code face_limit}.
 *                                  default 200_000 = web/Flutter 로딩 시간과 디테일의 균형.
 * @param orientation               이미지 정렬 모드. Tripo 옵션명 {@code orientation}.
 *                                  default {@code align_image} = 4방향 사진 일관성 향상.
 * @param geometryQuality           메시 형상 품질. Tripo 옵션명 {@code geometry_quality}.
 *                                  default {@code detailed} = v3.0+ Ultra Mode (최대 2M 폴리곤,
 *                                  스터드/갑피 변형 등 형상 디테일 보존). 운영 단가/처리시간 부담
 *                                  발생 시 {@code TRIPO_GEOMETRY_QUALITY=standard} 환경변수로 환원.
 */
@Validated
@ConfigurationProperties(prefix = "app.tripo-api")
public record TripoApiProperties(
        @Min(value = 100, message = "semaphoreAcquireTimeoutMs 는 100 이상이어야 합니다")
        @DefaultValue("2000")
        long semaphoreAcquireTimeoutMs,

        @DefaultValue("detailed")
        String textureQuality,

        @Min(value = 1, message = "faceLimit 는 1 이상이어야 합니다")
        @DefaultValue("200000")
        int faceLimit,

        @DefaultValue("align_image")
        String orientation,

        @DefaultValue("detailed")
        String geometryQuality
) {
}
