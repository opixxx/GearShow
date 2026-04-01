package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.domain.model.AuthAccount;
import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthAccountPersistenceAdapterIntegrationTest {

    @Autowired
    private AuthAccountJpaRepository authAccountJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private AuthAccountPersistenceAdapter adapter;
    private Long savedUserId;

    @BeforeEach
    void setUp() {
        adapter = new AuthAccountPersistenceAdapter(authAccountJpaRepository, new AuthAccountMapper());

        // 인증 계정에 필요한 사용자 먼저 생성
        UserPersistenceAdapter userAdapter = new UserPersistenceAdapter(userJpaRepository, new UserMapper());
        User user = userAdapter.save(User.create("테스트유저"));
        savedUserId = user.getId();
    }

    @Test
    @DisplayName("인증 계정을 저장하고 제공자 정보로 조회한다")
    void save_and_findByProviderTypeAndProviderUserKey() {
        // Given
        AuthAccount account = AuthAccount.create(savedUserId, ProviderType.KAKAO, "kakao-123");

        // When
        AuthAccount saved = adapter.save(account);
        Optional<AuthAccount> found = adapter.findByProviderTypeAndProviderUserKey(
                ProviderType.KAKAO, "kakao-123");

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(savedUserId);
    }

    @Test
    @DisplayName("존재하지 않는 제공자 정보로 조회하면 빈 Optional을 반환한다")
    void findByProviderTypeAndProviderUserKey_notFound_returnsEmpty() {
        // Given & When
        Optional<AuthAccount> found = adapter.findByProviderTypeAndProviderUserKey(
                ProviderType.KAKAO, "nonexistent-key");

        // Then
        assertThat(found).isEmpty();
    }
}
