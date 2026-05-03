package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.adapter.out.persistence.BootsSpecJpaRepository;
import com.gearshow.backend.catalog.adapter.out.persistence.CatalogItemJpaRepository;
import com.gearshow.backend.catalog.adapter.out.persistence.UniformSpecJpaRepository;
import com.gearshow.backend.catalog.application.dto.*;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.ListCatalogItemsUseCase;
import com.gearshow.backend.catalog.application.port.in.UpdateCatalogItemUseCase;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.catalog.domain.exception.DuplicateModelCodeException;
import com.gearshow.backend.catalog.domain.exception.NotFoundCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
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

    @Autowired
    private CatalogItemJpaRepository catalogItemJpaRepository;

    @Autowired
    private BootsSpecJpaRepository bootsSpecJpaRepository;

    @Autowired
    private UniformSpecJpaRepository uniformSpecJpaRepository;

    private CreateCatalogItemCommand createBootsCommand(String modelCode) {
        return new CreateCatalogItemCommand(
                Category.BOOTS, "Nike",
                modelCode, null,
                null, null,
                new CreateCatalogItemCommand.BootsSpecCommand(
                        StudType.FG, "Mercurial", null, "2025", "천연잔디", null),
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
                    Category.UNIFORM, "Nike",
                    null, null, null, null, null,
                    new CreateCatalogItemCommand.UniformSpecCommand(
                            "Liverpool", null, "2024-25", "EPL",
                            com.gearshow.backend.catalog.domain.vo.KitType.HOME, null));

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

        @Test
        @DisplayName("ADR-016: BOOTS 등록 시 한국어 풀네임 + 사일로 한국어가 영속된다")
        void create_boots_persistsKoreanFields() {
            // Given
            CreateCatalogItemCommand command = new CreateCatalogItemCommand(
                    Category.BOOTS, "Nike",
                    "AT5889-174", "https://example/img.jpg",
                    "나이키 프리미어 3 FG 화이트 메탈릭 골드",
                    "Nike Premier 3 FG White Metallic Gold",
                    new CreateCatalogItemCommand.BootsSpecCommand(
                            StudType.MG, "Mercurial Superfly", "머큐리얼 슈퍼플라이",
                            "2024", "MG", null),
                    null);

            // When
            CreateCatalogItemResult result = createCatalogItemUseCase.create(command);

            // Then — JpaEntity 까지 한국어 필드 영속 확인
            var item = catalogItemJpaRepository.findById(result.catalogItemId()).orElseThrow();
            assertThat(item.getFullNameKo()).isEqualTo("나이키 프리미어 3 FG 화이트 메탈릭 골드");
            assertThat(item.getFullNameEn()).isEqualTo("Nike Premier 3 FG White Metallic Gold");

            var bootsSpec = bootsSpecJpaRepository.findByCatalogItemId(result.catalogItemId()).orElseThrow();
            assertThat(bootsSpec.getStudType()).isEqualTo(StudType.MG);
            assertThat(bootsSpec.getSiloNameKo()).isEqualTo("머큐리얼 슈퍼플라이");
        }

        @Test
        @DisplayName("ADR-016: 빈티지 UNIFORM (kitType null) + 한국어 alias 가 영속된다")
        void create_uniformVintage_persistsNullableKitTypeAndKoreanAlias() {
            // Given — 1988/90 빈티지 저지, kitType 미명시
            CreateCatalogItemCommand command = new CreateCatalogItemCommand(
                    Category.UNIFORM, "Adidas",
                    "VINTAGE-MUFC-8890", null, null, null, null,
                    new CreateCatalogItemCommand.UniformSpecCommand(
                            "Manchester United", "맨체스터 유나이티드",
                            "1988/90", "EPL", null, null));

            // When
            CreateCatalogItemResult result = createCatalogItemUseCase.create(command);

            // Then
            var uniform = uniformSpecJpaRepository.findByCatalogItemId(result.catalogItemId()).orElseThrow();
            assertThat(uniform.getClubName()).isEqualTo("Manchester United");
            assertThat(uniform.getClubNameKo()).isEqualTo("맨체스터 유나이티드");
            assertThat(uniform.getSeason()).isEqualTo("1988/90");
            assertThat(uniform.getKitType()).isNull();
        }

        @Test
        @DisplayName("ADR-016: 국가대표 UNIFORM (league null) 이 영속된다")
        void create_uniformNationalTeam_persistsNullLeague() {
            // Given — 대한민국 국가대표
            CreateCatalogItemCommand command = new CreateCatalogItemCommand(
                    Category.UNIFORM, "Nike",
                    "KOREA-2425-HOME", null, null, null, null,
                    new CreateCatalogItemCommand.UniformSpecCommand(
                            "Korea", "대한민국",
                            "24-25", null, KitType.HOME, null));

            // When
            CreateCatalogItemResult result = createCatalogItemUseCase.create(command);

            // Then
            var uniform = uniformSpecJpaRepository.findByCatalogItemId(result.catalogItemId()).orElseThrow();
            assertThat(uniform.getClubName()).isEqualTo("Korea");
            assertThat(uniform.getClubNameKo()).isEqualTo("대한민국");
            assertThat(uniform.getLeague()).isNull();
            assertThat(uniform.getKitType()).isEqualTo(KitType.HOME);
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
            PageInfo<CatalogItemListResult> result = listCatalogItemsUseCase.list(null, 20);

            // Then
            assertThat(result.data()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.hasNext()).isFalse();
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
            UpdateCatalogItemCommand command = new UpdateCatalogItemCommand("Adidas", null, null, null, null);

            // When
            CatalogItemDetailResult result = updateCatalogItemUseCase.update(created.catalogItemId(), command);

            // Then
            assertThat(result.brand()).isEqualTo("Adidas");
        }

        @Test
        @DisplayName("존재하지 않는 카탈로그 아이템을 수정하면 예외가 발생한다")
        void update_notFound_throwsException() {
            // Given
            UpdateCatalogItemCommand command = new UpdateCatalogItemCommand("Adidas", null, null, null, null);

            // When & Then
            assertThatThrownBy(() -> updateCatalogItemUseCase.update(999L, command))
                    .isInstanceOf(NotFoundCatalogItemException.class);
        }

        @Test
        @DisplayName("ADR-016: 한국어 풀네임만 정정한다 (다른 필드 보존)")
        void update_correctsKoreanFullNameOnly() {
            // Given — 잘못된 한국어 alias 로 등록
            CreateCatalogItemCommand createCmd = new CreateCatalogItemCommand(
                    Category.BOOTS, "Nike",
                    "UPDATE-KO-001", "https://example/img.jpg",
                    "잘못된 한국어 풀네임",
                    "Nike Premier 3",
                    new CreateCatalogItemCommand.BootsSpecCommand(
                            StudType.FG, "Premier", null, "2024", "FG", null),
                    null);
            CreateCatalogItemResult created = createCatalogItemUseCase.create(createCmd);

            // When — 한국어 풀네임만 정정
            UpdateCatalogItemCommand command = new UpdateCatalogItemCommand(
                    null, null, null,
                    "나이키 프리미어 3 FG",
                    null);
            CatalogItemDetailResult result = updateCatalogItemUseCase.update(created.catalogItemId(), command);

            // Then — 한국어 풀네임만 갱신, 나머지는 보존
            assertThat(result.fullNameKo()).isEqualTo("나이키 프리미어 3 FG");
            assertThat(result.fullNameEn()).isEqualTo("Nike Premier 3");
            assertThat(result.brand()).isEqualTo("Nike");
            assertThat(result.modelCode()).isEqualTo("UPDATE-KO-001");

            // JpaEntity 영속까지 검증
            var item = catalogItemJpaRepository.findById(created.catalogItemId()).orElseThrow();
            assertThat(item.getFullNameKo()).isEqualTo("나이키 프리미어 3 FG");
            assertThat(item.getFullNameEn()).isEqualTo("Nike Premier 3");
        }

        @Test
        @DisplayName("ADR-016: brand 만 수정해도 한국어 풀네임 보존")
        void update_brandOnly_preservesKoreanFullNames() {
            // Given — 한국어/영문 풀네임 가진 아이템
            CreateCatalogItemCommand createCmd = new CreateCatalogItemCommand(
                    Category.BOOTS, "Nike",
                    "UPDATE-PRESERVE-001", null,
                    "나이키 프리미어 3",
                    "Nike Premier 3",
                    new CreateCatalogItemCommand.BootsSpecCommand(
                            StudType.FG, "Premier", null, "2024", "FG", null),
                    null);
            CreateCatalogItemResult created = createCatalogItemUseCase.create(createCmd);

            // When — brand 만 변경
            UpdateCatalogItemCommand command = new UpdateCatalogItemCommand(
                    "Adidas", null, null, null, null);
            CatalogItemDetailResult result = updateCatalogItemUseCase.update(created.catalogItemId(), command);

            // Then — 한국어/영문 풀네임 보존
            assertThat(result.brand()).isEqualTo("Adidas");
            assertThat(result.fullNameKo()).isEqualTo("나이키 프리미어 3");
            assertThat(result.fullNameEn()).isEqualTo("Nike Premier 3");
        }
    }

    @Nested
    @DisplayName("ADR-016 §B2: 응답 DTO 가 한국어 alias 노출")
    class ResponseExposure {

        @Test
        @DisplayName("getCatalogItem 응답에 fullNameKo/En + siloNameKo 가 포함된다")
        void getCatalogItem_boots_exposesKoreanAliases() {
            // Given
            CreateCatalogItemCommand command = new CreateCatalogItemCommand(
                    Category.BOOTS, "Nike",
                    "EXPOSE-001", null,
                    "나이키 머큐리얼 슈퍼플라이",
                    "Nike Mercurial Superfly",
                    new CreateCatalogItemCommand.BootsSpecCommand(
                            StudType.MG, "Mercurial Superfly", "머큐리얼 슈퍼플라이",
                            "2024", "MG", null),
                    null);
            CreateCatalogItemResult created = createCatalogItemUseCase.create(command);

            // When
            CatalogItemDetailResult result = getCatalogItemUseCase.getCatalogItem(created.catalogItemId());

            // Then — 응답에 한국어 alias 노출
            assertThat(result.fullNameKo()).isEqualTo("나이키 머큐리얼 슈퍼플라이");
            assertThat(result.fullNameEn()).isEqualTo("Nike Mercurial Superfly");
            assertThat(result.bootsSpec().siloNameKo()).isEqualTo("머큐리얼 슈퍼플라이");
        }

        @Test
        @DisplayName("getCatalogItem 응답에 clubNameKo 가 포함된다 (빈티지 케이스)")
        void getCatalogItem_uniformVintage_exposesClubNameKo() {
            // Given
            CreateCatalogItemCommand command = new CreateCatalogItemCommand(
                    Category.UNIFORM, "Adidas",
                    "EXPOSE-VINTAGE-001", null, null, null, null,
                    new CreateCatalogItemCommand.UniformSpecCommand(
                            "Manchester United", "맨체스터 유나이티드",
                            "1988/90", "EPL", null, null));
            CreateCatalogItemResult created = createCatalogItemUseCase.create(command);

            // When
            CatalogItemDetailResult result = getCatalogItemUseCase.getCatalogItem(created.catalogItemId());

            // Then
            assertThat(result.uniformSpec().clubNameKo()).isEqualTo("맨체스터 유나이티드");
            assertThat(result.uniformSpec().kitType()).isNull();
        }
    }
}
