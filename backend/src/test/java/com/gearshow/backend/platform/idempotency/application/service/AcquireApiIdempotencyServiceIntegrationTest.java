package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.adapter.out.persistence.IdempotencyKeyJpaEntity;
import com.gearshow.backend.platform.idempotency.adapter.out.persistence.IdempotencyKeyJpaRepository;
import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyAcquireResult;
import com.gearshow.backend.platform.idempotency.application.exception.ApiIdempotencyConflictException;
import com.gearshow.backend.platform.idempotency.application.exception.ApiIdempotencyOwnershipMismatchException;
import com.gearshow.backend.platform.idempotency.application.port.in.AcquireApiIdempotencyUseCase;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AcquireApiIdempotencyService} 통합 테스트 (Testcontainers MySQL).
 *
 * <p>NEW / CACHED / IN_PROGRESS_CONFLICT / OWNERSHIP_MISMATCH 네 경로 모두 검증.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
class AcquireApiIdempotencyServiceIntegrationTest {

    @Autowired
    private AcquireApiIdempotencyUseCase useCase;

    @Autowired
    private IdempotencyKeyJpaRepository repository;

    @BeforeEach
    void cleanup() {
        repository.deleteAll();
    }

    @Nested
    @DisplayName("acquire")
    class Acquire {

        @Test
        @DisplayName("신규 키는 NEW 를 반환한다")
        void new_key_returns_new() {
            // when
            ApiIdempotencyAcquireResult result = useCase.acquire(
                    "key-new", 10L, Duration.ofHours(24));

            // then
            assertThat(result).isInstanceOf(ApiIdempotencyAcquireResult.New.class);
            assertThat(repository.findByIdempotencyKey("key-new"))
                    .isPresent()
                    .get()
                    .extracting(IdempotencyKeyJpaEntity::getStatus)
                    .isEqualTo(IdempotencyKeyJpaEntity.Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("이미 완료된 키 (같은 userId) 는 CACHED 를 반환한다")
        void done_key_same_user_returns_cached() {
            // given: 첫 요청 후 markDone
            useCase.acquire("key-done", 10L, Duration.ofHours(24));
            useCase.markDone("key-done", 201, "{\"showcaseId\":42}");

            // when
            ApiIdempotencyAcquireResult result = useCase.acquire(
                    "key-done", 10L, Duration.ofHours(24));

            // then
            assertThat(result).isInstanceOf(ApiIdempotencyAcquireResult.Cached.class);
            ApiIdempotencyAcquireResult.Cached cached =
                    (ApiIdempotencyAcquireResult.Cached) result;
            assertThat(cached.httpStatus()).isEqualTo(201);
            assertThat(cached.responseBody()).contains("showcaseId");
        }

        @Test
        @DisplayName("IN_PROGRESS 상태 재도달 시 409 Conflict 예외")
        void in_progress_same_user_throws_conflict() {
            // given: 첫 요청 acquire
            useCase.acquire("key-inprogress", 10L, Duration.ofHours(24));

            // when + then
            assertThatThrownBy(() -> useCase.acquire("key-inprogress", 10L, Duration.ofHours(24)))
                    .isInstanceOf(ApiIdempotencyConflictException.class);
        }

        @Test
        @DisplayName("다른 사용자가 같은 키로 접근 시 소유권 불일치 예외")
        void different_user_throws_ownership_mismatch() {
            // given: userId=10 이 키 등록
            useCase.acquire("key-owner-10", 10L, Duration.ofHours(24));

            // when + then: userId=20 이 같은 키로 접근
            assertThatThrownBy(() -> useCase.acquire("key-owner-10", 20L, Duration.ofHours(24)))
                    .isInstanceOf(ApiIdempotencyOwnershipMismatchException.class);
        }
    }

    @Nested
    @DisplayName("markDone")
    class MarkDone {

        @Test
        @DisplayName("IN_PROGRESS 키를 DONE 으로 전이하고 응답을 캐싱한다")
        void marks_done_and_caches_response() {
            // given
            useCase.acquire("key-md", 10L, Duration.ofHours(24));

            // when
            useCase.markDone("key-md", 201, "{\"ok\":true}");

            // then
            IdempotencyKeyJpaEntity entity = repository.findByIdempotencyKey("key-md").orElseThrow();
            assertThat(entity.isDone()).isTrue();
            assertThat(entity.getHttpStatus()).isEqualTo(201);
            assertThat(entity.getResponseBody()).isEqualTo("{\"ok\":true}");
        }
    }
}
