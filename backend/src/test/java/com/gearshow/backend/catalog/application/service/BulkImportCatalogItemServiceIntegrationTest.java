package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.adapter.out.persistence.BootsSpecJpaRepository;
import com.gearshow.backend.catalog.adapter.out.persistence.CatalogItemJpaRepository;
import com.gearshow.backend.catalog.adapter.out.persistence.UniformSpecJpaRepository;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand.BootsSpecCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand.UniformSpecCommand;
import com.gearshow.backend.catalog.application.port.in.BulkImportCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BulkImportCatalogItemService} 통합 테스트.
 *
 * <p>핵심 검증: 건별 트랜잭션 분리 — 한 건의 실패가 다른 건의 commit 에 영향을 주지 않는다.
 * 따라서 본 테스트 클래스는 {@code @Transactional} 을 부착하지 않으며 (실제 commit 발생해야 함),
 * 데이터 정리는 {@code @BeforeEach}/{@code @AfterEach} 에서 직접 수행한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
class BulkImportCatalogItemServiceIntegrationTest {

    @Autowired
    private BulkImportCatalogItemUseCase bulkImportUseCase;

    @Autowired
    private CreateCatalogItemUseCase createCatalogItemUseCase;

    @Autowired
    private CatalogItemJpaRepository catalogItemJpaRepository;

    @Autowired
    private BootsSpecJpaRepository bootsSpecJpaRepository;

    @Autowired
    private UniformSpecJpaRepository uniformSpecJpaRepository;

    @BeforeEach
    void cleanUp() {
        bootsSpecJpaRepository.deleteAllInBatch();
        uniformSpecJpaRepository.deleteAllInBatch();
        catalogItemJpaRepository.deleteAllInBatch();
    }

    @AfterEach
    void cleanUpAfter() {
        bootsSpecJpaRepository.deleteAllInBatch();
        uniformSpecJpaRepository.deleteAllInBatch();
        catalogItemJpaRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("일괄 등록 — 혼합 페이로드")
    class MixedPayload {

        @Test
        @DisplayName("BOOTS+UNIFORM+중복+검증실패 4건 → created=2, skippedDuplicate=1, failed=1, DB에 2건만")
        void mixed4Items_returnsAccurateCountsAndPersistsCreatedOnly() {
            // Given — 사전 등록: BOOTS-DUP 가 이미 존재
            createCatalogItemUseCase.create(boots("Nike", "BOOTS-DUP"));

            CreateCatalogItemCommand bootsOk = boots("Nike", "BOOTS-OK");
            CreateCatalogItemCommand uniformOk = uniform("Liverpool");
            CreateCatalogItemCommand bootsDup = boots("Nike", "BOOTS-DUP");           // 사전 등록과 modelCode 중복
            CreateCatalogItemCommand bootsBad = boots("", "BOOTS-BAD");                // brand 공백 → InvalidCatalogItemException

            BulkImportCatalogItemCommand command = new BulkImportCatalogItemCommand(
                    List.of(bootsOk, uniformOk, bootsDup, bootsBad));

            // When
            BulkImportCatalogItemResult result = bulkImportUseCase.bulkImport(command);

            // Then — 카운트
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.skippedDuplicate()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.errors()).hasSize(2);

            assertThat(result.errors().get(0).rowIndex()).isEqualTo(2);
            assertThat(result.errors().get(0).errorCode()).isEqualTo("CATALOG_ITEM_DUPLICATE_MODEL_CODE");
            assertThat(result.errors().get(1).rowIndex()).isEqualTo(3);
            assertThat(result.errors().get(1).errorCode()).isEqualTo("CATALOG_ITEM_INVALID");

            // Then — DB 상태: 사전 등록 1 + bootsOk + uniformOk = 3건
            assertThat(catalogItemJpaRepository.count()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("트랜잭션 분리 — 부분 INSERT 롤백 보장")
    class TransactionIsolation {

        @Test
        @DisplayName("5건 중 3번째가 도메인 검증 실패 → CatalogItem 부분 INSERT 가 롤백되어 DB에 4건만 남는다")
        void thirdItemFailsDomainValidation_onlyOtherFourPersist() {
            // Given — 5건 UNIFORM, 3번째는 clubName 공백으로 InvalidCatalogItemException 유발
            // CatalogItem.create() 는 통과하고 catalogItemPort.save() 까지 성공한 후
            // UniformSpec.create() 시점에 예외가 발생한다.
            // setRollbackOnly() 가 호출되지 않으면 부분 INSERT 가 commit 되어 DB 에 5건이 남게 된다.
            BulkImportCatalogItemCommand command = new BulkImportCatalogItemCommand(List.of(
                    uniform("ClubA"),
                    uniform("ClubB"),
                    uniform(""),     // 3번째 — 도메인 검증 실패
                    uniform("ClubD"),
                    uniform("ClubE")
            ));

            // When
            BulkImportCatalogItemResult result = bulkImportUseCase.bulkImport(command);

            // Then — 카운트
            assertThat(result.created()).isEqualTo(4);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).rowIndex()).isEqualTo(2);

            // Then — DB 상태: 부분 INSERT 가 롤백되어 정확히 4건만 commit
            assertThat(catalogItemJpaRepository.count()).isEqualTo(4);
            assertThat(uniformSpecJpaRepository.count()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("풋살화 등록 — Category.BOOTS + StudType.TF")
    class FutsalRegistration {

        @Test
        @DisplayName("풋살화 1건 (BOOTS + TF) 정상 등록")
        void futsal_registers() {
            // Given
            CreateCatalogItemCommand futsal = new CreateCatalogItemCommand(
                    Category.BOOTS, "Nike", "FUTSAL-001", null,
                    new BootsSpecCommand(StudType.TF, "Premier", "2025", "인조잔디", null),
                    null);

            // When
            BulkImportCatalogItemResult result = bulkImportUseCase.bulkImport(
                    new BulkImportCatalogItemCommand(List.of(futsal)));

            // Then
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.failed()).isZero();
            assertThat(catalogItemJpaRepository.count()).isEqualTo(1);
            assertThat(bootsSpecJpaRepository.count()).isEqualTo(1);
        }
    }

    private static CreateCatalogItemCommand boots(String brand, String modelCode) {
        return new CreateCatalogItemCommand(
                Category.BOOTS, brand, modelCode, null,
                new BootsSpecCommand(StudType.FG, "Mercurial", "2025", "천연잔디", null),
                null);
    }

    private static CreateCatalogItemCommand uniform(String clubName) {
        return new CreateCatalogItemCommand(
                Category.UNIFORM, "Nike", null, null, null,
                new UniformSpecCommand(clubName, "24-25", "EPL", KitType.HOME, null));
    }
}
