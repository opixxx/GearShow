package com.gearshow.backend.showcase.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TripoPendingTaskJpaRepository} 및 {@link TripoPendingTaskJpaEntity} 통합 테스트.
 *
 * <p>workflow_id PK 직접 사용, 저장·조회·삭제 왕복을 검증한다.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripoPendingTaskJpaRepositoryTest {

    @Autowired
    private TripoPendingTaskJpaRepository repository;

    @Nested
    @DisplayName("저장 및 조회")
    class SaveAndFind {

        @Test
        @DisplayName("workflow_id 를 PK 로 저장하고 그대로 조회한다")
        void save_and_findById() {
            TripoPendingTaskJpaEntity entity = TripoPendingTaskJpaEntity.preservingTaskId(100L, "task-xyz");

            repository.saveAndFlush(entity);

            Optional<TripoPendingTaskJpaEntity> found = repository.findById(100L);
            assertThat(found).isPresent();
            assertThat(found.get().getTaskId()).isEqualTo("task-xyz");
            assertThat(found.get().getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("삭제 왕복")
    class Delete {

        @Test
        @DisplayName("TX2 에서 DELETE 후 재조회 시 빈 Optional")
        void delete_then_findById_returnsEmpty() {
            repository.saveAndFlush(TripoPendingTaskJpaEntity.preservingTaskId(101L, "task-to-delete"));

            repository.deleteById(101L);
            repository.flush();

            assertThat(repository.findById(101L)).isEmpty();
        }
    }
}
