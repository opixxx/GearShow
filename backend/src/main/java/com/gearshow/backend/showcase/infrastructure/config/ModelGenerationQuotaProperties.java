package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 사용자별 3D 모델 생성 일일 quota 설정.
 *
 * <p>악성 사용자가 생성 요청을 무한히 보내 Tripo 비용을 폭증시키는 것을 차단한다. KST 자정에
 * Redis TTL 로 자동 초기화된다 ({@code RedisModelGenerationDailyQuotaAdapter}).</p>
 *
 * <p>운영 임시 무력화: {@code MODEL_GENERATION_DAILY_LIMIT=999999} envvar 로 사실상 비활성화 가능.</p>
 *
 * @param dailyLimitPerUser 사용자별 KST 1일 생성 횟수 상한. 신규 + 재시도 모두 1회씩 차감.
 */
@Validated
@ConfigurationProperties(prefix = "app.model-generation")
public record ModelGenerationQuotaProperties(
        @Min(value = 1, message = "dailyLimitPerUser 는 1 이상이어야 합니다")
        @Max(value = 999_999, message = "dailyLimitPerUser 는 999999 이하여야 합니다")
        @DefaultValue("3")
        int dailyLimitPerUser
) {
}
