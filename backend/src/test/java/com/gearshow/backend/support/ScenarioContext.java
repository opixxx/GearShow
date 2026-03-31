package com.gearshow.backend.support;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import io.cucumber.spring.ScenarioScope;

/**
 * 하나의 Cucumber 시나리오 내에서 Step Definition 간 공유 상태를 관리한다.
 * Cucumber-Spring의 기본 스코프에 의해 시나리오 단위로 생성/소멸된다.
 *
 * <p>마지막 API 응답과 임의의 키-값 컨텍스트 데이터를 저장하여
 * 후속 Step에서 참조할 수 있도록 한다.
 *
 * <p>사용 예시:
 * <pre>
 *   // When 스텝에서
 *   context.setLastResponse(apiClient.get("/api/v1/health"));
 *
 *   // Then 스텝에서
 *   assertThat(context.getLastResponse().statusCode()).isEqualTo(200);
 * </pre>
 */
@Component
@ScenarioScope
public class ScenarioContext {

    private TestResponse<Map<String, Object>> lastResponse;
    private final Map<String, Object> store = new HashMap<>();

    /**
     * 마지막 API 응답을 저장한다. 후속 Then 스텝에서 검증에 사용된다.
     */
    public void setLastResponse(TestResponse<Map<String, Object>> response) {
        this.lastResponse = response;
    }

    /**
     * 마지막 API 응답을 반환한다.
     */
    public TestResponse<Map<String, Object>> getLastResponse() {
        return lastResponse;
    }

    /**
     * 임의의 값을 키로 저장하여 Step 간 데이터를 공유한다.
     */
    public void put(String key, Object value) {
        store.put(key, value);
    }

    /**
     * 저장된 값을 키로 조회한다.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) store.get(key);
    }
}
