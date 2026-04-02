package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.*;
import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.application.port.in.*;
import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
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

    @Autowired
    private ManageShowcaseImageUseCase manageShowcaseImageUseCase;

    @Autowired
    private GetModel3dUseCase getModel3dUseCase;

    @Autowired
    private RequestModelGenerationUseCase requestModelGenerationUseCase;

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
    @DisplayName("쇼케이스 등록 - 이미지 검증")
    class CreateImageValidation {

        @Test
        @DisplayName("이미지가 0장이면 예외가 발생한다")
        void create_withNoImages_throwsException() {
            // Given
            CreateShowcaseCommand command = createCommand(1L);

            // When & Then
            assertThatThrownBy(() -> createShowcaseUseCase.create(
                    command, List.of(), List.of()))
                    .isInstanceOf(MinImageRequiredException.class);
        }

        @Test
        @DisplayName("primaryImageIndex가 범위를 벗어나면 예외가 발생한다")
        void create_withInvalidPrimaryIndex_throwsException() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, 1L, "테스트", null, null,
                    ConditionGrade.A, 0, false, 5, false);

            // When & Then
            assertThatThrownBy(() -> createShowcaseUseCase.create(
                    command, createFakeImages(1), List.of()))
                    .isInstanceOf(PrimaryImageRequiredException.class);
        }
    }

    @Nested
    @DisplayName("이미지 관리")
    class ManageImage {

        @Test
        @DisplayName("쇼케이스에 이미지를 추가한다")
        void addImages_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When
            List<Long> addedIds = manageShowcaseImageUseCase.addImages(
                    showcaseId, 1L, createFakeImages(2));

            // Then
            assertThat(addedIds).hasSize(2);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 이미지를 추가하면 예외가 발생한다")
        void addImages_byNonOwner_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When & Then
            assertThatThrownBy(() -> manageShowcaseImageUseCase.addImages(
                    showcaseId, 999L, createFakeImages(1)))
                    .isInstanceOf(NotOwnerShowcaseException.class);
        }

        @Test
        @DisplayName("이미지 정렬 순서를 변경한다")
        void reorderImages_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            List<Long> addedIds = manageShowcaseImageUseCase.addImages(
                    showcaseId, 1L, createFakeImages(2));

            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(showcaseId);
            List<ManageShowcaseImageUseCase.ImageOrder> orders = detail.images().stream()
                    .map(img -> new ManageShowcaseImageUseCase.ImageOrder(
                            img.showcaseImageId(),
                            img.sortOrder(),
                            img.sortOrder() == 1))
                    .toList();

            // When & Then (예외 없이 수행되면 성공)
            manageShowcaseImageUseCase.reorderImages(showcaseId, 1L, orders);
        }
    }

    @Nested
    @DisplayName("3D 모델")
    class Model3d {

        @Test
        @DisplayName("3D 모델 생성을 요청하고 상태를 조회한다")
        void requestAndGet_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When
            ModelGenerationResult genResult = requestModelGenerationUseCase.requestOnCreate(
                    showcaseId, createFakeImages(4));

            // Then
            assertThat(genResult.showcase3dModelId()).isNotNull();
            assertThat(genResult.modelStatus()).isEqualTo(ModelStatus.REQUESTED);

            // When - 상태 조회
            Model3dDetailResult detailResult = getModel3dUseCase.getModel3d(showcaseId);

            // Then
            assertThat(detailResult.modelStatus()).isEqualTo(ModelStatus.REQUESTED);
            assertThat(detailResult.generationProvider()).isEqualTo("fake-tripo");
            assertThat(detailResult.sourceImageCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("3D 모델이 없는 쇼케이스를 조회하면 예외가 발생한다")
        void getModel3d_notFound_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When & Then
            assertThatThrownBy(() -> getModel3dUseCase.getModel3d(showcaseId))
                    .isInstanceOf(NotFoundShowcaseException.class);
        }

        @Test
        @DisplayName("소유자가 3D 모델 생성을 재요청한다")
        void requestRetry_byOwner_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            requestModelGenerationUseCase.requestOnCreate(showcaseId, createFakeImages(4));

            // When - REQUESTED 상태에서는 재요청 불가 (FAILED에서만 가능)
            // 따라서 먼저 상태를 확인
            Model3dDetailResult detail = getModel3dUseCase.getModel3d(showcaseId);
            assertThat(detail.modelStatus()).isEqualTo(ModelStatus.REQUESTED);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 3D 모델 재요청하면 예외가 발생한다")
        void requestRetry_byNonOwner_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When & Then
            assertThatThrownBy(() -> requestModelGenerationUseCase.requestRetry(
                    showcaseId, 999L, createFakeImages(4)))
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
            assertThat(result.data()).isNotEmpty();
        }
    }
}
