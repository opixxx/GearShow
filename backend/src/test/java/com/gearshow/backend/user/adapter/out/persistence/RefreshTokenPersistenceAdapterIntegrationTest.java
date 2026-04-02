package com.gearshow.backend.user.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenPersistenceAdapterIntegrationTest {

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    private RefreshTokenPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RefreshTokenPersistenceAdapter(refreshTokenJpaRepository);
    }

    @Test
    @DisplayName("Refresh Token을 저장하고 토큰으로 사용자 ID를 조회한다")
    void save_and_findUserIdByToken() {
        // Given
        String token = "test-refresh-token";
        Instant expiresAt = Instant.now().plus(Duration.ofDays(14));

        // When
        adapter.save(1L, token, expiresAt);
        Optional<Long> userId = adapter.findUserIdByToken(token);

        // Then
        assertThat(userId).isPresent();
        assertThat(userId.get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료된 Refresh Token으로 조회하면 빈 Optional을 반환한다")
    void findUserIdByToken_expired_returnsEmpty() {
        // Given
        String token = "expired-token";
        Instant expiresAt = Instant.now().minus(Duration.ofDays(1));

        // When
        adapter.save(1L, token, expiresAt);
        Optional<Long> userId = adapter.findUserIdByToken(token);

        // Then
        assertThat(userId).isEmpty();
    }

    @Test
    @DisplayName("사용자 ID로 Refresh Token을 삭제한다")
    void deleteByUserId_removesToken() {
        // Given
        String token = "token-to-delete";
        adapter.save(1L, token, Instant.now().plus(Duration.ofDays(14)));

        // When
        adapter.deleteByUserId(1L);
        Optional<Long> userId = adapter.findUserIdByToken(token);

        // Then
        assertThat(userId).isEmpty();
    }
}
