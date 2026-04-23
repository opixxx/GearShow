package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShowcaseJpaEntity} 의 {@code contentHash} 컬럼 저장·조회 왕복 검증.
 *
 * <p>P1-A 범위: 컬럼 · 인덱스 생성 확인. 10분 창 중복 검증 로직은 P1-B application 계층에서 추가.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ShowcaseJpaEntityContentHashTest {

    @Autowired
    private ShowcaseJpaRepository repository;

    @Nested
    @DisplayName("content_hash 컬럼")
    class ContentHashColumn {

        @Test
        @DisplayName("contentHash 값을 저장하고 조회 시 그대로 반환한다")
        void save_and_reload_withContentHash() {
            // given: SHA-256 hex 64자 contentHash 를 가진 쇼케이스
            String hash = "a".repeat(64);
            ShowcaseJpaEntity entity = baseBuilder().contentHash(hash).build();

            // when: 저장 후 ID 로 재조회
            ShowcaseJpaEntity saved = repository.saveAndFlush(entity);
            ShowcaseJpaEntity reloaded = repository.findById(saved.getId()).orElseThrow();

            // then: contentHash 가 왕복 보존된다
            assertThat(reloaded.getContentHash()).isEqualTo(hash);
        }

        @Test
        @DisplayName("contentHash 가 null 이어도 저장할 수 있다 (기존 데이터 보호)")
        void save_withoutContentHash_nullable() {
            // given: contentHash 가 null 인 쇼케이스 (신규 컬럼 추가 이전 데이터 시뮬레이션)
            ShowcaseJpaEntity entity = baseBuilder().contentHash(null).build();

            // when: 저장 후 재조회
            ShowcaseJpaEntity saved = repository.saveAndFlush(entity);
            ShowcaseJpaEntity reloaded = repository.findById(saved.getId()).orElseThrow();

            // then: null 허용으로 저장된다
            assertThat(reloaded.getContentHash()).isNull();
        }

        @Test
        @DisplayName("같은 content_hash 로 여러 건 저장 가능 (UNIQUE 제약 없음)")
        void sameContentHash_multipleRows_allowed() {
            // given: 같은 contentHash 로 두 개의 쇼케이스
            String hash = "b".repeat(64);

            // when: 둘 다 저장
            repository.saveAndFlush(baseBuilder().contentHash(hash).build());
            repository.saveAndFlush(baseBuilder().contentHash(hash).build());

            // then: UNIQUE 충돌 없이 2건 저장됨 (10분 창은 application 책임)
            long count = repository.findAll().stream()
                    .filter(e -> hash.equals(e.getContentHash()))
                    .count();
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("CHAR(64) 길이 초과 시 저장 실패한다 (strict mode)")
        void contentHash_exceeding64_fails() {
            // given: 65자 contentHash (CHAR(64) 제약 초과)
            String tooLong = "c".repeat(65);
            ShowcaseJpaEntity entity = baseBuilder().contentHash(tooLong).build();

            // when + then: DB 제약 위반으로 저장 실패
            assertThatThrownBy(() -> repository.saveAndFlush(entity))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    private ShowcaseJpaEntity.ShowcaseJpaEntityBuilder baseBuilder() {
        Instant now = Instant.now();
        return ShowcaseJpaEntity.builder()
                .ownerId(1L)
                .category(Category.BOOTS)
                .brand("Nike")
                .title("테스트 쇼케이스")
                .conditionGrade(ConditionGrade.A)
                .wearCount(0)
                .forSale(false)
                .has3dModel(false)
                .status(ShowcaseStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now);
    }
}
