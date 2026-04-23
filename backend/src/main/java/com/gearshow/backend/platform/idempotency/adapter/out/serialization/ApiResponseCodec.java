package com.gearshow.backend.platform.idempotency.adapter.out.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 멱등성 응답 캐싱용 JSON 직렬화/역직렬화 컴포넌트.
 *
 * <p>{@code Idempotency-Key} 기반 응답 캐싱을 지원하는 여러 컨트롤러가 공통 사용한다.
 * 직렬화 실패는 {@link ErrorCode#IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED} 로 감싸
 * {@link CustomException} 규약을 유지한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ApiResponseCodec {

    private final ObjectMapper objectMapper;

    public String encode(ApiResponse<?> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED, e);
        }
    }

    public <T> ApiResponse<T> decode(String body, TypeReference<ApiResponse<T>> typeRef) {
        try {
            return objectMapper.readValue(body, typeRef);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED, e);
        }
    }
}
