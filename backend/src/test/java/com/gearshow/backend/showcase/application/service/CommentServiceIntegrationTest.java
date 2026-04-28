package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.CommentResult;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.domain.exception.NotAuthorCommentException;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseCommentException;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.application.port.in.*;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.UserStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Transactional
class CommentServiceIntegrationTest {

    @Autowired
    private CreateShowcaseUseCase createShowcaseUseCase;

    @Autowired
    private CreateCommentUseCase createCommentUseCase;

    @Autowired
    private ListCommentsUseCase listCommentsUseCase;

    @Autowired
    private UpdateCommentUseCase updateCommentUseCase;

    @Autowired
    private DeleteCommentUseCase deleteCommentUseCase;

    @Autowired
    private UserPort userPort;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long showcaseId;

    @BeforeEach
    void setUp() {
        // 댓글 테스트를 위한 쇼케이스 사전 등록
        CreateShowcaseCommand command = new CreateShowcaseCommand(
                1L, null, Category.BOOTS, "Nike", null,
                "테스트 쇼케이스", null, null,
                ConditionGrade.A, 0, false, 0, false,
                null, null, null, "test-idempotency-key-comment");
        List<String> imageKeys = List.of("showcases/images/test.jpg");
        CreateShowcaseResult result = createShowcaseUseCase.create(command, imageKeys, List.of());
        showcaseId = result.showcaseId();
    }

    @Nested
    @DisplayName("댓글 작성")
    class Create {

        @Test
        @DisplayName("ACTIVE 쇼케이스에 댓글을 작성한다")
        void create_onActiveShowcase_success() {
            // Given & When
            Long commentId = createCommentUseCase.create(showcaseId, 1L, "테스트 댓글");

            // Then
            assertThat(commentId).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 쇼케이스에 댓글을 작성하면 예외가 발생한다")
        void create_onNonExistentShowcase_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> createCommentUseCase.create(999L, 1L, "댓글"))
                    .isInstanceOf(NotFoundShowcaseException.class);
        }
    }

    @Nested
    @DisplayName("댓글 목록 조회")
    class ListComments {

        @Test
        @DisplayName("댓글 목록을 조회한다")
        void list_returnsComments() {
            // Given
            createCommentUseCase.create(showcaseId, 1L, "첫 번째 댓글");
            createCommentUseCase.create(showcaseId, 2L, "두 번째 댓글");

            // When
            PageInfo<CommentResult> result = listCommentsUseCase.list(showcaseId, null, 20);

            // Then
            assertThat(result.data()).hasSize(2);
        }

        @Test
        @DisplayName("응답에 작성자 닉네임·프로필 이미지가 enrichment 되어 함께 내려온다")
        void list_enrichesAuthorProfile() {
            // Given - 댓글 작성자 두 명의 user 사전 등록
            User author1 = userPort.save(activeUser("풋살러", "https://cdn.example/u1.jpg"));
            User author2 = userPort.save(activeUser("머큐리얼좋아함", "https://cdn.example/u2.jpg"));
            createCommentUseCase.create(showcaseId, author1.getId(), "첫 댓글");
            createCommentUseCase.create(showcaseId, author2.getId(), "둘째 댓글");

            // When
            PageInfo<CommentResult> result = listCommentsUseCase.list(showcaseId, null, 20);

            // Then
            assertThat(result.data()).hasSize(2);
            CommentResult first = findByContent(result, "첫 댓글");
            CommentResult second = findByContent(result, "둘째 댓글");

            assertThat(first.author().userId()).isEqualTo(author1.getId());
            assertThat(first.author().nickname()).isEqualTo("풋살러");
            assertThat(first.author().profileImageUrl()).isEqualTo("https://cdn.example/u1.jpg");

            assertThat(second.author().userId()).isEqualTo(author2.getId());
            assertThat(second.author().nickname()).isEqualTo("머큐리얼좋아함");
            assertThat(second.author().profileImageUrl()).isEqualTo("https://cdn.example/u2.jpg");
        }

        @Test
        @DisplayName("댓글 N건 enrichment 시 user 조회는 단일 IN 쿼리로 1회만 발생한다 (N+1 회귀 가드)")
        void list_doesNotTriggerNPlusOneOnAuthorEnrichment() {
            // Given - 활성 사용자 5명 + 각자 댓글 1건씩
            for (int i = 0; i < 5; i++) {
                User u = userPort.save(activeUser("닉네임" + i, "https://cdn.example/u" + i + ".jpg"));
                createCommentUseCase.create(showcaseId, u.getId(), "댓글 " + i);
            }
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            Statistics stats = sessionFactory.getStatistics();
            stats.setStatisticsEnabled(true);
            stats.clear();

            // When
            listCommentsUseCase.list(showcaseId, null, 20);

            // Then - showcase 검증 1 + 댓글 1 + user IN 1 = 3 쿼리. 약간의 여유로 상한 5.
            long queryCount = stats.getPrepareStatementCount();
            assertThat(queryCount)
                    .as("댓글 5건 enrichment 가 user 조회를 1회로 묶어야 한다 (N+1 발생 시 8 이상)")
                    .isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("작성자가 탈퇴/삭제된 경우 author.userId 는 보존하고 nickname·profileImageUrl 은 null 로 내려온다")
        void list_returnsPlaceholderForDeletedAuthor() {
            // Given - user 를 사전 등록하지 않음 → 댓글 작성자 ID 는 user 테이블에 없는 상태
            Long ghostAuthorId = 99_999L;
            createCommentUseCase.create(showcaseId, ghostAuthorId, "탈퇴자가 남긴 댓글");

            // When
            PageInfo<CommentResult> result = listCommentsUseCase.list(showcaseId, null, 20);

            // Then
            assertThat(result.data()).hasSize(1);
            CommentResult.Author author = result.data().get(0).author();
            assertThat(author.userId()).isEqualTo(ghostAuthorId);
            assertThat(author.nickname()).isNull();
            assertThat(author.profileImageUrl()).isNull();
        }

        private CommentResult findByContent(PageInfo<CommentResult> page, String content) {
            return page.data().stream()
                    .filter(c -> c.content().equals(content))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private User activeUser(String nickname, String profileImageUrl) {
        Instant now = Instant.now();
        return User.builder()
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .phoneVerified(false)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Nested
    @DisplayName("댓글 수정")
    class Update {

        @Test
        @DisplayName("작성자가 댓글을 수정한다")
        void update_byAuthor_success() {
            // Given
            Long commentId = createCommentUseCase.create(showcaseId, 1L, "원본 댓글");

            // When
            updateCommentUseCase.update(showcaseId, commentId, 1L, "수정된 댓글");

            // Then - 수정 확인은 목록 조회로
            PageInfo<CommentResult> result = listCommentsUseCase.list(showcaseId, null, 20);
            assertThat(result.data()).anyMatch(c -> c.content().equals("수정된 댓글"));
        }

        @Test
        @DisplayName("작성자가 아닌 사용자가 수정하면 예외가 발생한다")
        void update_byNonAuthor_throwsException() {
            // Given
            Long commentId = createCommentUseCase.create(showcaseId, 1L, "원본");

            // When & Then
            assertThatThrownBy(() -> updateCommentUseCase.update(showcaseId, commentId, 999L, "수정"))
                    .isInstanceOf(NotAuthorCommentException.class);
        }

        @Test
        @DisplayName("존재하지 않는 댓글을 수정하면 예외가 발생한다")
        void update_notFound_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> updateCommentUseCase.update(showcaseId, 999L, 1L, "수정"))
                    .isInstanceOf(NotFoundShowcaseCommentException.class);
        }
    }

    @Nested
    @DisplayName("댓글 삭제")
    class Delete {

        @Test
        @DisplayName("작성자가 댓글을 삭제한다")
        void delete_byAuthor_success() {
            // Given
            Long commentId = createCommentUseCase.create(showcaseId, 1L, "삭제할 댓글");

            // When
            deleteCommentUseCase.delete(showcaseId, commentId, 1L);

            // Then - 삭제 후 목록에서 사라짐 (소프트 삭제지만 ACTIVE만 조회)
            PageInfo<CommentResult> result = listCommentsUseCase.list(showcaseId, null, 20);
            assertThat(result.data()).noneMatch(c -> c.showcaseCommentId().equals(commentId));
        }

        @Test
        @DisplayName("작성자가 아닌 사용자가 삭제하면 예외가 발생한다")
        void delete_byNonAuthor_throwsException() {
            // Given
            Long commentId = createCommentUseCase.create(showcaseId, 1L, "삭제할 댓글");

            // When & Then
            assertThatThrownBy(() -> deleteCommentUseCase.delete(showcaseId, commentId, 999L))
                    .isInstanceOf(NotAuthorCommentException.class);
        }
    }
}
