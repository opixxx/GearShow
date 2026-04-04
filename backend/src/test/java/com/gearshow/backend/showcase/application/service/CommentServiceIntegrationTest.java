package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.CommentResult;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.UploadFile;
import com.gearshow.backend.showcase.application.exception.NotAuthorCommentException;
import com.gearshow.backend.showcase.application.exception.NotFoundShowcaseCommentException;
import com.gearshow.backend.showcase.application.port.in.*;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
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

    private Long showcaseId;

    @BeforeEach
    void setUp() {
        // 댓글 테스트를 위한 쇼케이스 사전 등록
        CreateShowcaseCommand command = new CreateShowcaseCommand(
                1L, null, Category.BOOTS, "Nike", null,
                "테스트 쇼케이스", null, null,
                ConditionGrade.A, 0, false, 0, false,
                null, null);
        List<UploadFile> images = List.of(new UploadFile(
                new ByteArrayInputStream("fake".getBytes()),
                "image/jpeg", 4L, "test.jpg"));
        CreateShowcaseResult result = createShowcaseUseCase.create(command, images, List.of());
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
                    .isInstanceOf(Exception.class);
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
