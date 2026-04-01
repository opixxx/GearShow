package com.gearshow.backend;

import com.gearshow.backend.support.TestOAuthConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cucumber 테스트용 Spring 컨텍스트 설정.
 * 랜덤 포트로 전체 애플리케이션을 기동하며, Testcontainers MySQL을 사용한다.
 * 실제 OAuth API 대신 StubOAuthClient를 사용한다.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
public class CucumberSpringConfiguration {
}
