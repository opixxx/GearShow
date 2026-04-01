package com.gearshow.backend.support;

import com.gearshow.backend.user.application.port.out.OAuthClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 테스트용 OAuth 설정.
 * 실제 카카오/애플 OAuth Adapter 대신 StubOAuthClient를 주입한다.
 */
@TestConfiguration
public class TestOAuthConfig {

    @Bean
    @Primary
    public List<OAuthClient> oAuthClients() {
        return List.of(
                new StubOAuthClient("kakao"),
                new StubOAuthClient("apple")
        );
    }
}
