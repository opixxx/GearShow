package com.gearshow.backend.catalog.adapter.in.web;

import com.gearshow.backend.catalog.adapter.in.web.dto.CatalogItemDetailResponse;
import com.gearshow.backend.catalog.adapter.in.web.dto.CreateCatalogItemRequest;
import com.gearshow.backend.catalog.adapter.in.web.dto.UpdateCatalogItemRequest;
import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.application.dto.CatalogItemListResult;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemResult;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.ListCatalogItemsUseCase;
import com.gearshow.backend.catalog.application.port.in.UpdateCatalogItemUseCase;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 카탈로그 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
@Validated
public class CatalogController {

    private final CreateCatalogItemUseCase createCatalogItemUseCase;
    private final GetCatalogItemUseCase getCatalogItemUseCase;
    private final ListCatalogItemsUseCase listCatalogItemsUseCase;
    private final UpdateCatalogItemUseCase updateCatalogItemUseCase;

    /**
     * 카탈로그 아이템 목록을 조회한다.
     * 커서 기반 페이징을 지원한다.
     *
     * @param category  카테고리 필터
     * @param brand     브랜드 필터
     * @param keyword   아이템명/모델코드 검색
     * @param pageToken 페이지 토큰 (첫 페이지는 생략)
     * @param size      페이지 크기 (기본값 20)
     * @return 카탈로그 아이템 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageInfo<CatalogItemListResult>>> list(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            int size) {

        PageInfo<CatalogItemListResult> result = listCatalogItemsUseCase.list(
                pageToken, size, category, brand, keyword);

        return ResponseEntity.ok(
                ApiResponse.of(200, "카탈로그 아이템 목록 조회 성공", result));
    }

    /**
     * 카탈로그 아이템을 등록한다.
     *
     * @param request 등록 요청
     * @return 등록된 카탈로그 아이템 ID
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> create(
            @Valid @RequestBody CreateCatalogItemRequest request) {

        CreateCatalogItemResult result = createCatalogItemUseCase.create(request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(201, "카탈로그 아이템 등록 성공",
                        Map.of("catalogItemId", result.catalogItemId())));
    }

    /**
     * 카탈로그 아이템 상세를 조회한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @return 상세 정보
     */
    @GetMapping("/{catalogItemId}")
    public ResponseEntity<ApiResponse<CatalogItemDetailResponse>> getDetail(
            @PathVariable Long catalogItemId) {

        CatalogItemDetailResult result = getCatalogItemUseCase.getCatalogItem(catalogItemId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "카탈로그 아이템 조회 성공",
                        CatalogItemDetailResponse.from(result)));
    }

    /**
     * 카탈로그 아이템을 수정한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @param request       수정 요청
     * @return 수정된 카탈로그 아이템 상세
     */
    @PatchMapping("/{catalogItemId}")
    public ResponseEntity<ApiResponse<CatalogItemDetailResponse>> update(
            @PathVariable Long catalogItemId,
            @Valid @RequestBody UpdateCatalogItemRequest request) {

        CatalogItemDetailResult result = updateCatalogItemUseCase.update(
                catalogItemId, request.toCommand());

        return ResponseEntity.ok(
                ApiResponse.of(200, "카탈로그 아이템 수정 성공",
                        CatalogItemDetailResponse.from(result)));
    }
}
