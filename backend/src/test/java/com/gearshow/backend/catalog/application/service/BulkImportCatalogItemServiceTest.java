package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult.BulkImportError;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand.BootsSpecCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand.UniformSpecCommand;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link BulkImportCatalogItemService} 단위 테스트.
 *
 * <p>{@link BulkImportRowProcessor} 를 mock 으로 주입하여 카운트 매핑 / errors 누적 로직만 검증한다.
 * 트랜잭션 분리·도메인 검증은 통합 테스트의 책임이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BulkImportCatalogItemServiceTest {

    @Mock
    private BulkImportRowProcessor rowProcessor;

    @InjectMocks
    private BulkImportCatalogItemService service;

    @Nested
    @DisplayName("bulkImport — 카운트 매핑")
    class CountMapping {

        @Test
        @DisplayName("모든 건이 CREATED 면 created 카운트만 채워지고 errors 는 비어있다")
        void allCreated_returnsCreatedCountOnly() {
            // Given
            List<CreateCatalogItemCommand> items = List.of(
                    bootsCommand("MODEL-1"),
                    bootsCommand("MODEL-2"),
                    bootsCommand("MODEL-3")
            );
            given(rowProcessor.processRow(0, items.get(0))).willReturn(RowOutcome.created());
            given(rowProcessor.processRow(1, items.get(1))).willReturn(RowOutcome.created());
            given(rowProcessor.processRow(2, items.get(2))).willReturn(RowOutcome.created());

            // When
            BulkImportCatalogItemResult result = service.bulkImport(new BulkImportCatalogItemCommand(items));

            // Then
            assertThat(result.created()).isEqualTo(3);
            assertThat(result.skippedDuplicate()).isZero();
            assertThat(result.failed()).isZero();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("DUPLICATE 1건 + CREATED 1건 → skippedDuplicate=1, errors[0] 에 DUPLICATE 기록")
        void duplicatePlusCreated_recordsDuplicateInErrors() {
            // Given
            List<CreateCatalogItemCommand> items = List.of(
                    bootsCommand("MODEL-DUP"),
                    bootsCommand("MODEL-OK")
            );
            BulkImportError dupError = new BulkImportError(
                    0, Category.BOOTS, "Nike", "MODEL-DUP",
                    "CATALOG_ITEM_DUPLICATE_MODEL_CODE", "동일 카테고리 내 중복된 모델 코드입니다");
            given(rowProcessor.processRow(0, items.get(0))).willReturn(RowOutcome.duplicate(dupError));
            given(rowProcessor.processRow(1, items.get(1))).willReturn(RowOutcome.created());

            // When
            BulkImportCatalogItemResult result = service.bulkImport(new BulkImportCatalogItemCommand(items));

            // Then
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.skippedDuplicate()).isEqualTo(1);
            assertThat(result.failed()).isZero();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0)).isEqualTo(dupError);
        }

        @Test
        @DisplayName("FAILED 1건 + CREATED 1건 → failed=1, errors[0] 에 FAILED 기록")
        void failedPlusCreated_recordsFailedInErrors() {
            // Given
            List<CreateCatalogItemCommand> items = List.of(
                    bootsCommand("MODEL-OK"),
                    bootsCommand("MODEL-BAD")
            );
            BulkImportError failError = new BulkImportError(
                    1, Category.BOOTS, "Nike", "MODEL-BAD",
                    "CATALOG_ITEM_INVALID", "유효하지 않은 카탈로그 아이템입니다");
            given(rowProcessor.processRow(0, items.get(0))).willReturn(RowOutcome.created());
            given(rowProcessor.processRow(1, items.get(1))).willReturn(RowOutcome.failed(failError));

            // When
            BulkImportCatalogItemResult result = service.bulkImport(new BulkImportCatalogItemCommand(items));

            // Then
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.skippedDuplicate()).isZero();
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0)).isEqualTo(failError);
        }

        @Test
        @DisplayName("혼합 4건 (BOOTS+UNIFORM+DUPLICATE+FAILED) → created=2, skippedDuplicate=1, failed=1, errors 2건")
        void mixed4Items_returnsAccurateCounts() {
            // Given
            CreateCatalogItemCommand bootsOk = bootsCommand("BOOTS-OK");
            CreateCatalogItemCommand uniformOk = uniformCommand("UNIFORM-OK");
            CreateCatalogItemCommand bootsDup = bootsCommand("BOOTS-DUP");
            CreateCatalogItemCommand bootsBad = new CreateCatalogItemCommand(
                    Category.BOOTS, "", "BOOTS-BAD", null, null, null,
                    new BootsSpecCommand(StudType.FG, "Mercurial", null, "2025", "천연잔디", null),
                    null);
            List<CreateCatalogItemCommand> items = List.of(bootsOk, uniformOk, bootsDup, bootsBad);

            BulkImportError dupError = new BulkImportError(
                    2, Category.BOOTS, "Nike", "BOOTS-DUP",
                    "CATALOG_ITEM_DUPLICATE_MODEL_CODE", "동일 카테고리 내 중복된 모델 코드입니다");
            BulkImportError failError = new BulkImportError(
                    3, Category.BOOTS, "", "BOOTS-BAD",
                    "CATALOG_ITEM_INVALID", "유효하지 않은 카탈로그 아이템입니다");

            given(rowProcessor.processRow(0, bootsOk)).willReturn(RowOutcome.created());
            given(rowProcessor.processRow(1, uniformOk)).willReturn(RowOutcome.created());
            given(rowProcessor.processRow(2, bootsDup)).willReturn(RowOutcome.duplicate(dupError));
            given(rowProcessor.processRow(3, bootsBad)).willReturn(RowOutcome.failed(failError));

            // When
            BulkImportCatalogItemResult result = service.bulkImport(new BulkImportCatalogItemCommand(items));

            // Then
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.skippedDuplicate()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.errors())
                    .hasSize(2)
                    .containsExactly(dupError, failError);
            assertThat(result.errors().get(0).rowIndex()).isEqualTo(2);
            assertThat(result.errors().get(1).rowIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 리스트 입력 시 모든 카운트 0, errors 비어있음")
        void emptyList_returnsZeroCounts() {
            // Given & When
            BulkImportCatalogItemResult result = service.bulkImport(
                    new BulkImportCatalogItemCommand(List.of()));

            // Then
            assertThat(result.created()).isZero();
            assertThat(result.skippedDuplicate()).isZero();
            assertThat(result.failed()).isZero();
            assertThat(result.errors()).isEmpty();
        }
    }

    private static CreateCatalogItemCommand bootsCommand(String modelCode) {
        return new CreateCatalogItemCommand(
                Category.BOOTS, "Nike", modelCode, null, null, null,
                new BootsSpecCommand(StudType.FG, "Mercurial", null, "2025", "천연잔디", null),
                null);
    }

    private static CreateCatalogItemCommand uniformCommand(String modelCode) {
        return new CreateCatalogItemCommand(
                Category.UNIFORM, "Nike", modelCode, null, null, null,
                null,
                new UniformSpecCommand("Liverpool", null, "24-25", "EPL", KitType.HOME, null));
    }
}
