package com.gearshow.backend.admin.infrastructure.config;

import com.gearshow.backend.admin.application.config.AdminBootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 관리자 BC 인프라 설정.
 *
 * <ul>
 *   <li>{@link PasswordEncoder} — BCrypt 빈 등록</li>
 *   <li>{@link AdminBootstrapProperties} 활성화</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
