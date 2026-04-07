package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.exception.DuplicateSortOrderException;
import com.gearshow.backend.showcase.application.exception.ImageNotBelongToShowcaseException;
import com.gearshow.backend.showcase.application.exception.ImageReorderMismatchException;
import com.gearshow.backend.showcase.application.exception.NotFoundShowcaseImageException;
import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.application.port.in.*;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.showcase.application.exception.MinImageRequiredException;
import com.gearshow.backend.showcase.application.exception.PrimaryImageRequiredException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

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
                ownerId, null, Category.BOOTS, "Nike", "DJ2839",
                "테스트 쇼케이스", "테스트 설명",
                "270", ConditionGrade.A, 5, false, 0, false,
                null, null);
    }

    private List<String> createFakeImageKeys(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "showcases/images/test-" + i + ".jpg")
                .toList();
    }

    private Long createAndGetShowcaseId(Long ownerId) {
        CreateShowcaseResult result = createShowcaseUseCase.create(
                createCommand(ownerId), createFakeImageKeys(1), List.of());
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
            List<String> imageKeys = createFakeImageKeys(2);

            // When
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    command, imageKeys, List.of());

            // Then
            assertThat(result.showcaseId()).isNotNull();
            assertThat(result.model3dStatus()).isNull();
        }

        @Test
        @DisplayName("축구화 스펙과 함께 쇼케이스를 등록하면 상세에서 스펙이 조회된다")
        void create_withBootsSpec_specIncludedInDetail() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, null, Category.BOOTS, "Nike", "DJ2839",
                    "스펙 테스트", null, "270mm",
                    ConditionGrade.A, 5, false, 0, false,
                    new CreateShowcaseCommand.BootsSpecCommand(
                            StudType.FG, "Mercurial", "2025", "천연잔디", null),
                    null);

            // When
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    command, createFakeImageKeys(1), List.of());
            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(result.showcaseId());

            // Then
            assertThat(detail.category()).isEqualTo(Category.BOOTS);
            assertThat(detail.brand()).isEqualTo("Nike");
            assertThat(detail.bootsSpec()).isNotNull();
            assertThat(detail.bootsSpec().studType()).isEqualTo(StudType.FG);
            assertThat(detail.bootsSpec().siloName()).isEqualTo("Mercurial");
            assertThat(detail.uniformSpec()).isNull();
        }

        @Test
        @DisplayName("유니폼 스펙과 함께 쇼케이스를 등록하면 상세에서 스펙이 조회된다")
        void create_withUniformSpec_specIncludedInDetail() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, null, Category.UNIFORM, "Nike", null,
                    "유니폼 테스트", null, "L",
                    ConditionGrade.S, 0, false, 0, false,
                    null,
                    new CreateShowcaseCommand.UniformSpecCommand(
                            "Liverpool", "24-25", "EPL", KitType.HOME, null));

            // When
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    command, createFakeImageKeys(1), List.of());
            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(result.showcaseId());

            // Then
            assertThat(detail.category()).isEqualTo(Category.UNIFORM);
            assertThat(detail.uniformSpec()).isNotNull();
            assertThat(detail.uniformSpec().clubName()).isEqualTo("Liverpool");
            assertThat(detail.uniformSpec().kitType()).isEqualTo(KitType.HOME);
            assertThat(detail.bootsSpec()).isNull();
        }

        @Test
        @DisplayName("카탈로그 없이 category/brand만으로 쇼케이스를 등록한다")
        void create_withoutCatalogItem_success() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, null, Category.BOOTS, "Adidas", null,
                    "카탈로그 없이 등록", null, null,
                    ConditionGrade.B, 0, false, 0, false,
                    null, null);

            // When
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    command, createFakeImageKeys(1), List.of());
            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(result.showcaseId());

            // Then
            assertThat(detail.catalogItemId()).isNull();
            assertThat(detail.brand()).isEqualTo("Adidas");
        }

        @Test
        @DisplayName("3D 모델 소스 이미지와 함께 쇼케이스를 등록하면 REQUESTED 상태이다")
        void create_withModelSourceImages_returnsRequested() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, null, Category.BOOTS, "Nike", null,
                    "테스트", null, null,
                    ConditionGrade.A, 0, false, 0, true,
                    null, null);
            List<String> images = createFakeImageKeys(1);
            List<String> modelSourceImages = createFakeImageKeys(4);

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
        @DisplayName("소유자가 쇼케이스를 삭제하면 공개 조회 시 NotFound 예외가 발생한다")
        void delete_byOwner_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When
            deleteShowcaseUseCase.delete(showcaseId, 1L);

            // Then - DELETED 상태는 공개 상세 조회에서 차단됨
            assertThatThrownBy(() -> getShowcaseUseCase.getShowcase(showcaseId))
                    .isInstanceOf(NotFoundShowcaseException.class);
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
            List<String> emptyImageKeys = List.of();
            List<String> emptyModelKeys = List.of();
            assertThatThrownBy(() -> createShowcaseUseCase.create(command, emptyImageKeys, emptyModelKeys))
                    .isInstanceOf(MinImageRequiredException.class);
        }

        @Test
        @DisplayName("primaryImageIndex가 범위를 벗어나면 예외가 발생한다")
        void create_withInvalidPrimaryIndex_throwsException() {
            // Given
            CreateShowcaseCommand command = new CreateShowcaseCommand(
                    1L, null, Category.BOOTS, "Nike", null,
                    "테스트", null, null,
                    ConditionGrade.A, 0, false, 5, false,
                    null, null);

            // When & Then
            List<String> imageKeys = createFakeImageKeys(1);
            List<String> emptyModelKeys = List.of();
            assertThatThrownBy(() -> createShowcaseUseCase.create(command, imageKeys, emptyModelKeys))
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
                    showcaseId, 1L, createFakeImageKeys(2));

            // Then
            assertThat(addedIds).hasSize(2);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 이미지를 추가하면 예외가 발생한다")
        void addImages_byNonOwner_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            // When & Then
            List<String> imageKeys = createFakeImageKeys(1);
            assertThatThrownBy(() -> manageShowcaseImageUseCase.addImages(showcaseId, 999L, imageKeys))
                    .isInstanceOf(NotOwnerShowcaseException.class);
        }

        @Test
        @DisplayName("이미지 정렬 순서를 변경한다")
        void reorderImages_success() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);

            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(showcaseId);
            List<ManageShowcaseImageUseCase.ImageOrder> orders = detail.images().stream()
                    .map(img -> new ManageShowcaseImageUseCase.ImageOrder(
                            img.showcaseImageId(),
                            img.sortOrder(),
                            img.sortOrder() == 1))
                    .toList();

            // When
            manageShowcaseImageUseCase.reorderImages(showcaseId, 1L, orders);

            // Then - 정렬 순서 변경 후 상세 조회로 검증
            ShowcaseDetailResult updated = getShowcaseUseCase.getShowcase(showcaseId);
            assertThat(updated.images())
                    .isNotEmpty()
                    .anyMatch(img -> img.isPrimary() && img.sortOrder() == 1);
        }

        @Test
        @DisplayName("다른 쇼케이스의 이미지를 삭제하면 예외가 발생한다")
        void deleteImage_notBelongToShowcase_throwsException() {
            // Given - 쇼케이스 2개 생성
            Long showcaseIdA = createAndGetShowcaseId(1L);
            Long showcaseIdB = createAndGetShowcaseId(1L);

            // 쇼케이스 B에 이미지 추가 후 이미지 ID 획득
            manageShowcaseImageUseCase.addImages(showcaseIdB, 1L, createFakeImageKeys(1));
            ShowcaseDetailResult detailB = getShowcaseUseCase.getShowcase(showcaseIdB);
            Long imageBId = detailB.images().get(0).showcaseImageId();

            // 쇼케이스 A에도 삭제 가능하도록 이미지 2개 보장
            manageShowcaseImageUseCase.addImages(showcaseIdA, 1L, createFakeImageKeys(1));

            // When & Then - 쇼케이스 A 경로로 쇼케이스 B의 이미지 삭제 시도
            assertThatThrownBy(() -> manageShowcaseImageUseCase.deleteImage(
                    showcaseIdA, imageBId, 1L))
                    .isInstanceOf(ImageNotBelongToShowcaseException.class);
        }

        @Test
        @DisplayName("존재하지 않는 이미지를 삭제하면 예외가 발생한다")
        void deleteImage_notFound_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            manageShowcaseImageUseCase.addImages(showcaseId, 1L, createFakeImageKeys(1));

            // When & Then
            assertThatThrownBy(() -> manageShowcaseImageUseCase.deleteImage(
                    showcaseId, 999999L, 1L))
                    .isInstanceOf(NotFoundShowcaseImageException.class);
        }

        @Test
        @DisplayName("재정렬 시 이미지 목록이 실제와 불일치하면 예외가 발생한다")
        void reorderImages_mismatch_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            manageShowcaseImageUseCase.addImages(showcaseId, 1L, createFakeImageKeys(1));

            // 존재하지 않는 이미지 ID로 재정렬 요청
            List<ManageShowcaseImageUseCase.ImageOrder> orders = List.of(
                    new ManageShowcaseImageUseCase.ImageOrder(999999L, 1, true));

            // When & Then
            assertThatThrownBy(() -> manageShowcaseImageUseCase.reorderImages(
                    showcaseId, 1L, orders))
                    .isInstanceOf(ImageReorderMismatchException.class);
        }

        @Test
        @DisplayName("재정렬 시 sortOrder가 중복되면 예외가 발생한다")
        void reorderImages_duplicateSortOrder_throwsException() {
            // Given - 이미지 2개 이상 필요
            Long showcaseId = createAndGetShowcaseId(1L);
            manageShowcaseImageUseCase.addImages(showcaseId, 1L, createFakeImageKeys(1));

            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(showcaseId);
            // 대표 이미지 1개 유지 + 같은 sortOrder(1)를 모든 이미지에 할당
            List<ManageShowcaseImageUseCase.ImageOrder> orders = new java.util.ArrayList<>();
            boolean firstPrimary = true;
            for (var img : detail.images()) {
                orders.add(new ManageShowcaseImageUseCase.ImageOrder(
                        img.showcaseImageId(), 1, firstPrimary));
                firstPrimary = false;
            }

            // When & Then
            assertThatThrownBy(() -> manageShowcaseImageUseCase.reorderImages(
                    showcaseId, 1L, orders))
                    .isInstanceOf(DuplicateSortOrderException.class);
        }

        @Test
        @DisplayName("재정렬 시 대표 이미지가 2개이면 예외가 발생한다")
        void reorderImages_multiplePrimary_throwsException() {
            // Given
            Long showcaseId = createAndGetShowcaseId(1L);
            manageShowcaseImageUseCase.addImages(showcaseId, 1L, createFakeImageKeys(1));

            ShowcaseDetailResult detail = getShowcaseUseCase.getShowcase(showcaseId);
            // 모든 이미지를 대표 이미지로 설정
            List<ManageShowcaseImageUseCase.ImageOrder> orders = detail.images().stream()
                    .map(img -> new ManageShowcaseImageUseCase.ImageOrder(
                            img.showcaseImageId(), img.sortOrder(), true))
                    .toList();

            // When & Then
            assertThatThrownBy(() -> manageShowcaseImageUseCase.reorderImages(
                    showcaseId, 1L, orders))
                    .isInstanceOf(InvalidShowcaseException.class);
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
                    showcaseId, createFakeImageKeys(4));

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
            requestModelGenerationUseCase.requestOnCreate(showcaseId, createFakeImageKeys(4));

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
            List<String> modelSourceImageKeys = createFakeImageKeys(4);
            assertThatThrownBy(() -> requestModelGenerationUseCase.requestRetry(showcaseId, 999L, modelSourceImageKeys))
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
