package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.*;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.ListCatalogItemsUseCase;
import com.gearshow.backend.catalog.application.port.in.UpdateCatalogItemUseCase;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.catalog.domain.exception.DuplicateModelCodeException;
import com.gearshow.backend.catalog.domain.exception.NotFoundCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
@Transactional
class CatalogItemServiceIntegrationTest {

    @Autowired
    private CreateCatalogItemUseCase createCatalogItemUseCase;

    @Autowired
    private GetCatalogItemUseCase getCatalogItemUseCase;

    @Autowired
    private ListCatalogItemsUseCase listCatalogItemsUseCase;

    @Autowired
    private UpdateCatalogItemUseCase updateCatalogItemUseCase;

    private CreateCatalogItemCommand createBootsCommand(String modelCode) {
        return new CreateCatalogItemCommand(
                Category.BOOTS, "Nike", "Mercurial Superfly 10",
                modelCode, null,
                new CreateCatalogItemCommand.BootsSpecCommand(
                        StudType.FG, "Mercurial", "2025", "천연잔디", null),
                null);
    }

    @Nested
    @DisplayName("카탈로그 아이템 등록")
    class Create {

        @Test
        @DisplayName("축구화 카탈로그 아이템을 등록한다")
        void create_boots_success() {
            // Given
            CreateCatalogItemCommand command = createBootsCommand("DJ2839-001");

            // When
            CreateCatalogItemResult result = createCatalogItemUseCase.create(command);

            // Then
            assertThat(result.catalogItemId()).isNotNull();
        }

        @Test
        @DisplayName("유니폼 카탈로그 아이템을 등록한다")
        void create_uniform_success() {
            // Given
            CreateCatalogItemCommand command = new CreateCatalogItemCommand(
                    Category.UNIFORM, "Nike", "Liverpool 24-25 Home",
                    null, null, null,
                    new CreateCatalogItemCommand.UniformSpecCommand(
                            "Liverpool", "2024-25", "EPL", "Nike", null));

            // When
            CreateCatalogItemResult result = createCatalogItemUseCase.create(command);

            // Then
            assertThat(result.catalogItemId()).isNotNull();
        }

        @Test
        @DisplayName("동일 카테고리 내 중복 모델 코드로 등록하면 예외가 발생한다")
        void create_duplicateModelCode_throwsException() {
            // Given
            createCatalogItemUseCase.create(createBootsCommand("DUPLICATE-001"));

            // When & Then
            assertThatThrownBy(() -> createCatalogItemUseCase.create(createBootsCommand("DUPLICATE-001")))
                    .isInstanceOf(DuplicateModelCodeException.class);
        }
    }

    @Nested
    @DisplayName("카탈로그 아이템 상세 조회")
    class GetDetail {

        @Test
        @DisplayName("축구화 카탈로그 아이템 상세를 조회한다")
        void getCatalogItem_boots_returnsDetail() {
            // Given
            CreateCatalogItemResult created = createCatalogItemUseCase.create(createBootsCommand("DETAIL-001"));

            // When
            CatalogItemDetailResult result = getCatalogItemUseCase.getCatalogItem(created.catalogItemId());

            // Then
            assertThat(result.category()).isEqualTo(Category.BOOTS);
            assertThat(result.brand()).isEqualTo("Nike");
            assertThat(result.bootsSpec()).isNotNull();
            assertThat(result.bootsSpec().studType()).isEqualTo(StudType.FG);
            assertThat(result.uniformSpec()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 카탈로그 아이템을 조회하면 예외가 발생한다")
        void getCatalogItem_notFound_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> getCatalogItemUseCase.getCatalogItem(999L))
                    .isInstanceOf(NotFoundCatalogItemException.class);
        }
    }

    @Nested
    @DisplayName("카탈로그 아이템 목록 조회")
    class ListItems {

        @Test
        @DisplayName("커서 페이징으로 목록을 조회한다")
        void list_returnsPaginatedResult() {
            // Given
            createCatalogItemUseCase.create(createBootsCommand("LIST-001"));
            createCatalogItemUseCase.create(createBootsCommand("LIST-002"));

            // When
            PageInfo<CatalogItemListResult> result = listCatalogItemsUseCase.list(
                    null, 20, null, null, null);

            // Then
            assertThat(result.data()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("카테고리로 필터링하여 조회한다")
        void list_filterByCategory() {
            // Given
            createCatalogItemUseCase.create(createBootsCommand("FILTER-001"));

            // When
            PageInfo<CatalogItemListResult> result = listCatalogItemsUseCase.list(
                    null, 20, Category.BOOTS, null, null);

            // Then
            assertThat(result.data())
                    .isNotEmpty()
                    .allMatch(item -> item.category() == Category.BOOTS);
        }
    }

    @Nested
    @DisplayName("카탈로그 아이템 수정")
    class Update {

        @Test
        @DisplayName("카탈로그 아이템의 브랜드를 수정한다")
        void update_changesBrand() {
            // Given
            CreateCatalogItemResult created = createCatalogItemUseCase.create(createBootsCommand("UPDATE-001"));
            UpdateCatalogItemCommand command = new UpdateCatalogItemCommand("Adidas", null, null, null);

            // When
            CatalogItemDetailResult result = updateCatalogItemUseCase.update(created.catalogItemId(), command);

            // Then
            assertThat(result.brand()).isEqualTo("Adidas");
            assertThat(result.itemName()).isEqualTo("Mercurial Superfly 10");
        }

        @Test
        @DisplayName("존재하지 않는 카탈로그 아이템을 수정하면 예외가 발생한다")
        void update_notFound_throwsException() {
            // Given
            UpdateCatalogItemCommand command = new UpdateCatalogItemCommand("Adidas", null, null, null);

            // When & Then
            assertThatThrownBy(() -> updateCatalogItemUseCase.update(999L, command))
                    .isInstanceOf(NotFoundCatalogItemException.class);
        }
    }
}
