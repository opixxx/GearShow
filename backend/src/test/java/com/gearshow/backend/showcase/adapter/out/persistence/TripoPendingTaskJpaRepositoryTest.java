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
            // given: 새 워크플로우의 Tripo task_id 선저장
            TripoPendingTaskJpaEntity entity =
                    TripoPendingTaskJpaEntity.preservingTaskId(100L, "task-xyz");

            // when: 저장 후 workflow_id 로 조회
            repository.saveAndFlush(entity);
            Optional<TripoPendingTaskJpaEntity> found = repository.findById(100L);

            // then: 저장한 task_id 와 createdAt 이 보존된다
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
            // given: 저장된 선저장 엔트리
            repository.saveAndFlush(
                    TripoPendingTaskJpaEntity.preservingTaskId(101L, "task-to-delete"));

            // when: workflow_id 로 삭제
            repository.deleteById(101L);
            repository.flush();

            // then: 재조회 시 없음
            assertThat(repository.findById(101L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("PK 제약")
    class PrimaryKey {

        @Test
        @DisplayName("같은 workflow_id 재저장 시 JPA merge 로 upsert 된다")
        void duplicateWorkflowId_upserts() {
            // given: 200번 워크플로우에 task_id=task-a 선저장
            repository.saveAndFlush(
                    TripoPendingTaskJpaEntity.preservingTaskId(200L, "task-a"));

            // when: 같은 workflow_id 로 다른 task_id=task-b 저장
            // Spring Data JPA 는 @GeneratedValue 없는 @Id 필드가 비어있지 않으면 new 가 아니라고 판단하고 merge 로 처리한다.
            // 따라서 PK 충돌 예외가 아니라 UPSERT 로 동작한다 (기존 row 의 taskId 가 덮어쓰임).
            repository.saveAndFlush(
                    TripoPendingTaskJpaEntity.preservingTaskId(200L, "task-b"));

            // then: 200번 row 는 1건이고 task_id 는 최신 값으로 갱신됨
            // 주의: 운영 경로에서는 이 상황이 발생하지 않도록 워커/Reconcile 에서
            // existsById 체크 후 기존 task_id 를 재사용하는 것이 설계 의도 (ADR-011 ④).
            assertThat(repository.findById(200L))
                    .isPresent()
                    .get()
                    .extracting(TripoPendingTaskJpaEntity::getTaskId)
                    .isEqualTo("task-b");
            assertThat(repository.count()).isEqualTo(1L);
        }
    }
}
