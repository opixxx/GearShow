package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tripo API 에러 응답 DTO.
 *
 * <p>Tripo 가 4xx/5xx 를 반환할 때 body 에 담기는 에러 정보를 파싱한다.
 * {@code code} 필드가 Tripo 내부 에러 코드이며, 이를 기반으로
 * Retryable / Non-retryable 분류를 수행한다.</p>
 *
 * @param code    Tripo 내부 에러 코드 (1000~2020)
 * @param message 에러 설명
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripoErrorResponse(
        int code,
        String message
) {
}
