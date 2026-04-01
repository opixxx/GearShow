package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.domain.model.User;
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
class UserPersistenceAdapterIntegrationTest {

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(userJpaRepository, new UserMapper());
    }

    @Test
    @DisplayName("사용자를 저장하고 ID로 조회한다")
    void save_and_findById() {
        // Given
        User user = User.create("테스트유저");

        // When
        User saved = adapter.save(user);
        Optional<User> found = adapter.findById(saved.getId());

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("테스트유저");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
    void findById_notFound_returnsEmpty() {
        // Given & When
        Optional<User> found = adapter.findById(999L);

        // Then
        assertThat(found).isEmpty();
    }
}
