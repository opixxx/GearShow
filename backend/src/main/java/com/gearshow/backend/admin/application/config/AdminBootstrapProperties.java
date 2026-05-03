package com.gearshow.backend.admin.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 첫 관리자 부트스트랩 환경변수 바인딩.
 *
 * <p>두 필드 모두 {@code @DefaultValue("")} 로 빈 문자열 허용. 빈 문자열이면 부트스트랩 skip.
 * 운영 배포 시 {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD} 환경변수 주입 필요.</p>
 *
 * <p>위치: application/config — application 계층의 설정 record. 이로써 application/service 가
 * 외곽 계층(infrastructure) 을 import 하지 않게 된다.</p>
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record AdminBootstrapProperties(
        @DefaultValue("") String email,
        @DefaultValue("") String password
) {
}
