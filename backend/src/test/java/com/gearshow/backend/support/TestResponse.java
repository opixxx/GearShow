package com.gearshow.backend.support;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * HTTP 응답을 감싸는 테스트 전용 래퍼.
 * Step Definition이 Spring HTTP 내부 구현에 직접 의존하지 않도록 추상화한다.
 *
 * @param <T> 응답 본문 타입
 */
public class TestResponse<T> {

    private final HttpStatusCode statusCode;
    private final T body;

    private TestResponse(HttpStatusCode statusCode, T body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * Spring ResponseEntity로부터 TestResponse를 생성한다.
     */
    public static <T> TestResponse<T> from(ResponseEntity<T> responseEntity) {
        return new TestResponse<>(responseEntity.getStatusCode(), responseEntity.getBody());
    }

    public int statusCode() {
        return statusCode.value();
    }

    public T body() {
        return body;
    }

    /**
     * 응답 본문이 Map일 때 특정 필드 값을 추출한다.
     *
     * @param key 필드명
     * @return 필드 값, 없으면 null
     */
    @SuppressWarnings("unchecked")
    public Object field(String key) {
        if (body instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }
}
