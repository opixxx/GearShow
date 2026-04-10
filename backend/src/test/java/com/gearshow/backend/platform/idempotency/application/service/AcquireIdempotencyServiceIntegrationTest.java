package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AcquireIdempotencyService 통합 테스트.
 * 멱등성은 시스템 신뢰성의 근간이므로 Happy/Unhappy/도메인 격리를 모두 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
class AcquireIdempotencyServiceIntegrationTest {

    @Autowired
    private AcquireIdempotencyUseCase acquireIdempotencyUseCase;

    @Nested
    @DisplayName("멱등성 권한 획득")
    class TryAcquire {

        @Test
        @DisplayName("처음 보는 메시지 ID는 권한을 획득한다")
        void tryAcquire_newMessage_returnsTrue() {
            // Given
            String messageId = UUID.randomUUID().toString();

            // When
            boolean acquired = acquireIdempotencyUseCase.tryAcquire(
                    messageId, IdempotencyDomain.SHOWCASE_MODEL_GENERATION);

            // Then
            assertThat(acquired).isTrue();
        }

        @Test
        @DisplayName("같은 메시지 ID로 두 번 호출하면 두 번째는 실패한다")
        void tryAcquire_duplicateMessage_returnsFalseOnSecondCall() {
            // Given
            String messageId = UUID.randomUUID().toString();

            // When
            boolean firstAcquire = acquireIdempotencyUseCase.tryAcquire(
                    messageId, IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
            boolean secondAcquire = acquireIdempotencyUseCase.tryAcquire(
                    messageId, IdempotencyDomain.SHOWCASE_MODEL_GENERATION);

            // Then
            assertThat(firstAcquire).isTrue();
            assertThat(secondAcquire).isFalse();
        }

        @Test
        @DisplayName("같은 도메인에서 같은 메시지를 여러 번 호출해도 중복 처리되지 않는다")
        void tryAcquire_repeatedCalls_onlyFirstSucceeds() {
            // Given
            String messageId = UUID.randomUUID().toString();
            IdempotencyDomain domain = IdempotencyDomain.SHOWCASE_MODEL_GENERATION;

            // When
            int successCount = 0;
            for (int i = 0; i < 5; i++) {
                if (acquireIdempotencyUseCase.tryAcquire(messageId, domain)) {
                    successCount++;
                }
            }

            // Then
            assertThat(successCount).isEqualTo(1);
        }
    }
}
