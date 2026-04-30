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
 * <p>{@link Clock#systemUTC()} 를 사용해 JVM default timezone 의존성을 제거한다. 비즈니스 코드는
 * zone 변환 ({@code clock.withZone(ZoneId.of("Asia/Seoul"))}) 을 명시적으로 수행해야 하며, 운영
 * 환경 (KST), CI (UTC), 로컬 (KST) 의 default zone 차이가 결과에 영향을 주지 않는다.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
