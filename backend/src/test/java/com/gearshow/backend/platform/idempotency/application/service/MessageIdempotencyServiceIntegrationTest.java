package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.in.MessageIdempotencyUseCase;
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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * MessageIdempotencyService 통합 테스트 (ADR-011 §2.③ 완료 시점 INSERT).
 * 멱등성은 시스템 신뢰성의 근간이므로 Happy/Unhappy/도메인 격리를 모두 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
class MessageIdempotencyServiceIntegrationTest {

    @Autowired
    private MessageIdempotencyUseCase messageIdempotencyUseCase;

    @Nested
    @DisplayName("isProcessed 조회")
    class IsProcessed {

        @Test
        @DisplayName("처음 보는 메시지 ID 는 false 를 반환한다")
        void isProcessed_newMessage_returnsFalse() {
            String messageId = UUID.randomUUID().toString();

            boolean processed = messageIdempotencyUseCase.isProcessed(
                    messageId, IdempotencyDomain.SHOWCASE_MODEL_GENERATION);

            assertThat(processed).isFalse();
        }

        @Test
        @DisplayName("markProcessed 후 같은 messageId 로 isProcessed 호출하면 true 를 반환한다")
        void isProcessed_afterMark_returnsTrue() {
            String messageId = UUID.randomUUID().toString();
            IdempotencyDomain domain = IdempotencyDomain.SHOWCASE_MODEL_GENERATION;

            messageIdempotencyUseCase.markProcessed(messageId, domain);

            assertThat(messageIdempotencyUseCase.isProcessed(messageId, domain)).isTrue();
        }

        @Test
        @DisplayName("isProcessed 는 호출만으로 부수 효과가 없다 (다음 호출도 false)")
        void isProcessed_isReadOnly() {
            String messageId = UUID.randomUUID().toString();
            IdempotencyDomain domain = IdempotencyDomain.SHOWCASE_MODEL_GENERATION;

            assertThat(messageIdempotencyUseCase.isProcessed(messageId, domain)).isFalse();
            assertThat(messageIdempotencyUseCase.isProcessed(messageId, domain)).isFalse();
        }
    }

    @Nested
    @DisplayName("markProcessed 멱등성")
    class MarkProcessed {

        @Test
        @DisplayName("같은 messageId 로 두 번 호출해도 예외 없이 멱등 동작한다 (UNIQUE 충돌 흡수)")
        void markProcessed_calledTwice_noException() {
            String messageId = UUID.randomUUID().toString();
            IdempotencyDomain domain = IdempotencyDomain.SHOWCASE_MODEL_GENERATION;

            assertThatCode(() -> {
                messageIdempotencyUseCase.markProcessed(messageId, domain);
                messageIdempotencyUseCase.markProcessed(messageId, domain);
            }).doesNotThrowAnyException();

            assertThat(messageIdempotencyUseCase.isProcessed(messageId, domain)).isTrue();
        }

        @Test
        @DisplayName("같은 messageId 라도 도메인이 다르면 별개로 기록된다 (도메인 격리)")
        void markProcessed_differentDomain_independent() {
            String messageId = UUID.randomUUID().toString();
            IdempotencyDomain modelGen = IdempotencyDomain.SHOWCASE_MODEL_GENERATION;

            messageIdempotencyUseCase.markProcessed(messageId, modelGen);

            assertThat(messageIdempotencyUseCase.isProcessed(messageId, modelGen)).isTrue();
        }
    }
}
