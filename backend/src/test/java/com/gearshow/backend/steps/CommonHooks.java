package com.gearshow.backend.steps;

import com.gearshow.backend.support.TestApiClient;
import io.cucumber.java.After;

/**
 * 모든 Cucumber 시나리오에 공통으로 적용되는 훅.
 * 시나리오 종료 후 인증 상태를 정리하여 테스트 격리를 보장한다.
 */
public class CommonHooks {

    private final TestApiClient apiClient;

    public CommonHooks(TestApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 매 시나리오 종료 후 인증 토큰을 정리한다.
     */
    @After
    public void clearAuth() {
        apiClient.clearAuth();
    }
}
