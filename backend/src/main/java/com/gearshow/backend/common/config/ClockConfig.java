package com.gearshow.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시각 추상화 빈 등록.
 *
 * <p>{@link Clock} 을 빈으로 노출해 시간 의존 비즈니스 로직 (예: KST 자정 quota 초기화) 의 단위
 * 테스트에서 fixed clock 주입을 가능하게 한다.</p>
 *
 * <p>{@link Clock#systemDefaultZone()} 은 시스템 기본 zone 의 wall-clock. 비즈니스 코드는 zone
 * 변환 ({@code ZoneId.of("Asia/Seoul")}) 을 직접 수행해야 한다.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
