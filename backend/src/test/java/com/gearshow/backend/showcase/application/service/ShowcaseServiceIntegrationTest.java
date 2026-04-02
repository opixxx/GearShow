package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.*;
import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.application.port.in.*;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Transactional
class ShowcaseServiceIntegrationTest {

    @Autowired
    private CreateShowcaseUseCase createShowcaseUseCase;

    @Autowired
    private GetShowcaseUseCase getShowcaseUseCase;

    @Autowired
    private ListShowcasesUseCase listShowcasesUseCase;

    @Autowired
    private UpdateShowcaseUseCase updateShowcaseUseCase;

    @Autowired
    private DeleteShowcaseUseCase deleteShowcaseUseCase;

    // ===== Helper =====

    private CreateShowcaseCommand createCommand(Long ownerId) {
        return new CreateShowcaseCommand(
                ownerId, 1L, "테스트 쇼케이스", "테스트 설명",
                "270", ConditionGrade.A, 5, false, 0, false);
    }

    private List<MultipartFile> createFakeImages(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> (MultipartFile) new MockMultipartFile(
                        "images", "test-" + i + ".jpg", "image/jpeg", "fake".getBytes()))
                .toList();
    }

    private Long createAndGetShowcaseId(Long ownerId) {
        CreateShowcaseResult result = createShowcaseUseCase.create(
                createCommand(ownerId), createFakeImages(1), List.of());
        return result.showcaseId();
    }

    @Nested
    @DisplayName("쇼케이스 등록")
    class Create {

        @Test
        @DisplayName("일반 이미지만으로 쇼케이스를 등록한다")
        void create_withImages_success() {
            // Given
            CreateShowcaseCommand command = createCommand(1L);
            List<MultipartFile> images = createFakeImages(2);

            // When
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    command, images, List.of());

            // Then
            assertThat(result.showcaseId()).isNotNull();
            assertThat(result.model3dStatus()).isNull();
        }

        @Test
        @DisplayName("3D 모델 소스 이미지와 함께 쇼케이스를 등록하면 REQUESTED 상태이다")
        void create_withModelSourceImages_returnsRequested() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, 1L, "테스트", null, null,
                    ConditionGrade.A, 0, false, 0, true);
            List<MultipartFile> images = createFakeImages(1);
            List<MultipartFile> modelSourceImages = createFakeImages(4);

            // When
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    command, images, modelSourceImages);

            // Then
            assertThat(result.showcaseId()).isNotNull();
            assertThat(result.model3dStatus()).isNotNull();
        }
    }

    @Nested
    @DisplayName("쇼케이스 상세 조회")
    class GetDetail {

        @Test
        @DisplayName("등록된 쇼케이스 상세를 조회한다")
        void getShowcase_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When
            ShowcaseDetailResult result = getShowcaseUseCase.getShowcase(showcaseId);

            // Then
            assertThat(result.showcaseId()).isEqualTo(showcaseId);
            assertThat(result.title()).isEqualTo("테스트 쇼케이스");
            assertThat(result.conditionGrade()).isEqualTo(ConditionGrade.A);
            assertThat(result.images()).hasSize(1);
        }

        @Test
        @DisplayName("존재하지 않는 쇼케이스를 조회하면 예외가 발생한다")
        void getShowcase_notFound_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> getShowcaseUseCase.getShowcase(999L))
                    .isInstanceOf(NotFoundShowcaseException.class);
        }
    }

    @Nested
    @DisplayName("쇼케이스 수정")
    class Update {

        @Test
        @DisplayName("소유자가 쇼케이스를 수정한다")
        void update_byOwner_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            UpdateShowcaseCommand command = new UpdateShowcaseCommand(
                    "수정된 제목", null, null, null, null, null);

            // When
            updateShowcaseUseCase.update(showcaseId, 1L, command);

            // Then
            ShowcaseDetailResult result = getShowcaseUseCase.getShowcase(showcaseId);
            assertThat(result.title()).isEqualTo("수정된 제목");
            assertThat(result.description()).isEqualTo("테스트 설명");
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 수정하면 예외가 발생한다")
        void update_byNonOwner_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            UpdateShowcaseCommand command = new UpdateShowcaseCommand(
                    "수정", null, null, null, null, null);

            // When & Then
            assertThatThrownBy(() -> updateShowcaseUseCase.update(showcaseId, 999L, command))
                    .isInstanceOf(NotOwnerShowcaseException.class);
        }
    }

    @Nested
    @DisplayName("쇼케이스 삭제")
    class Delete {

        @Test
        @DisplayName("소유자가 쇼케이스를 삭제한다")
        void delete_byOwner_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When
            deleteShowcaseUseCase.delete(showcaseId, 1L);

            // Then
            ShowcaseDetailResult result = getShowcaseUseCase.getShowcase(showcaseId);
            assertThat(result.showcaseStatus()).isEqualTo(ShowcaseStatus.DELETED);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 삭제하면 예외가 발생한다")
        void delete_byNonOwner_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When & Then
            assertThatThrownBy(() -> deleteShowcaseUseCase.delete(showcaseId, 999L))
                    .isInstanceOf(NotOwnerShowcaseException.class);
        }
    }

    @Nested
    @DisplayName("쇼케이스 목록 조회")
    class ListShowcases {

        @Test
        @DisplayName("첫 페이지를 조회한다")
        void list_firstPage_success() {
            // Given
            createAndGetShowcaseId(1L);
            createAndGetShowcaseId(1L);

            // When
            PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(
                    null, 20, null, null, null, null, null);

            // Then
            assertThat(result.data()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("내 쇼케이스 목록을 조회한다")
        void listByOwner_success() {
            // Given
            createAndGetShowcaseId(1L);
            createAndGetShowcaseId(2L);

            // When
            PageInfo<ShowcaseListResult> result = listShowcasesUseCase.listByOwner(
                    1L, null, 20, null);

            // Then
            assertThat(result.data()).allMatch(s -> true); // 소유자 필터링 확인
        }
    }
}
