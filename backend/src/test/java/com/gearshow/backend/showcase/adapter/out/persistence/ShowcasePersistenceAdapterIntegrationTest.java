package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ShowcasePersistenceAdapterIntegrationTest {

    @Autowired
    private ShowcaseJpaRepository showcaseJpaRepository;

    private ShowcasePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ShowcasePersistenceAdapter(showcaseJpaRepository, new ShowcaseMapper());
    }

    @Nested
    @DisplayName("저장 및 조회")
    class SaveAndFind {

        @Test
        @DisplayName("쇼케이스를 저장하고 ID로 조회한다")
        void save_and_findById() {
            // Given
            Showcase showcase = createShowcase("테스트 쇼케이스");

            // When
            Showcase saved = adapter.save(showcase);
            Optional<Showcase> found = adapter.findById(saved.getId());

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("테스트 쇼케이스");
            assertThat(found.get().getStatus()).isEqualTo(ShowcaseStatus.ACTIVE);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
        void findById_notFound_returnsEmpty() {
            // Given & When
            Optional<Showcase> found = adapter.findById(999L);

            // Then
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("커서 기반 페이징")
    class CursorPagination {

        @Test
        @DisplayName("첫 페이지를 조회한다")
        void findAllFirstPage_returnsItems() {
            // Given
            adapter.save(createShowcase("쇼케이스1"));
            adapter.save(createShowcase("쇼케이스2"));
            adapter.save(createShowcase("쇼케이스3"));

            // When
            List<Showcase> result = adapter.findAllFirstPage(2);

            // Then - size+1 조회이므로 3개가 반환될 수 있음 (hasNext 판단용)
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("커서 기반으로 다음 페이지를 조회한다")
        void findAllWithCursor_returnsNextPage() {
            // Given - 고정 시각으로 테스트 결정성 보장
            Instant baseTime = Instant.parse("2026-01-01T12:00:00Z");
            adapter.save(createShowcaseWithTime("쇼케이스1", baseTime.minusSeconds(2)));
            adapter.save(createShowcaseWithTime("쇼케이스2", baseTime.minusSeconds(1)));
            Showcase third = adapter.save(createShowcaseWithTime("쇼케이스3", baseTime));

            // When - 가장 최신(third) 이후의 데이터를 조회
            List<Showcase> result = adapter.findAllWithCursor(
                    third.getCreatedAt(), third.getId(), 10);

            // Then - third보다 이전 데이터만 반환
            assertThat(result)
                    .isNotEmpty()
                    .allMatch(s -> !s.getId().equals(third.getId()));
        }

        @Test
        @DisplayName("소유자 기준 첫 페이지를 조회한다")
        void findByOwnerIdFirstPage_returnsOwnerItems() {
            // Given
            adapter.save(createShowcaseWithOwner("내 쇼케이스", 100L));
            adapter.save(createShowcaseWithOwner("다른 사람 쇼케이스", 200L));

            // When
            List<Showcase> result = adapter.findByOwnerIdFirstPage(
                    100L, 10, null);

            // Then
            assertThat(result)
                    .isNotEmpty()
                    .allMatch(s -> s.getOwnerId().equals(100L));
        }
    }

    // ===== Helper =====

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T12:00:00Z");

    private Showcase createShowcase(String title) {
        return Showcase.builder()
                .ownerId(1L)
                .catalogItemId(null)
                .category(Category.BOOTS)
                .brand("Nike")
                .title(title)
                .conditionGrade(ConditionGrade.A)
                .wearCount(0)
                .forSale(false)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(FIXED_TIME)
                .updatedAt(FIXED_TIME)
                .build();
    }

    private Showcase createShowcaseWithTime(String title, Instant createdAt) {
        return Showcase.builder()
                .ownerId(1L)
                .catalogItemId(null)
                .category(Category.BOOTS)
                .brand("Nike")
                .title(title)
                .conditionGrade(ConditionGrade.A)
                .wearCount(0)
                .forSale(false)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private Showcase createShowcaseWithOwner(String title, Long ownerId) {
        return Showcase.builder()
                .ownerId(ownerId)
                .catalogItemId(null)
                .category(Category.BOOTS)
                .brand("Nike")
                .title(title)
                .conditionGrade(ConditionGrade.A)
                .wearCount(0)
                .forSale(false)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(FIXED_TIME)
                .updatedAt(FIXED_TIME)
                .build();
    }
}
