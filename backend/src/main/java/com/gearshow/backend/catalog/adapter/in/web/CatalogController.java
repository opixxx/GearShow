package com.gearshow.backend.catalog.adapter.in.web;

import com.gearshow.backend.catalog.adapter.in.web.dto.CatalogItemDetailResponse;
import com.gearshow.backend.catalog.adapter.in.web.dto.CreateCatalogItemRequest;
import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemResult;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 카탈로그 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogController {

    private final CreateCatalogItemUseCase createCatalogItemUseCase;
    private final GetCatalogItemUseCase getCatalogItemUseCase;

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
}
