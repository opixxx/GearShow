package com.gearshow.backend.catalog.adapter.in.web;

import com.gearshow.backend.catalog.adapter.in.web.dto.BulkImportCatalogItemRequest;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult;
import com.gearshow.backend.catalog.application.port.in.BulkImportCatalogItemUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카탈로그 관리자 전용 API 컨트롤러.
 *
 * <p>경로 분리(/api/admin/catalog)를 통해 후속 PR 에서 RBAC 가드(예: AdminOnly)
 * 를 도입할 때 영향 범위를 최소화한다.</p>
 */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
@Validated
public class AdminCatalogController {

    private final BulkImportCatalogItemUseCase bulkImportCatalogItemUseCase;

    /**
     * 카탈로그 아이템을 일괄 등록한다.
     *
     * <p>각 항목은 독립된 트랜잭션으로 처리되며, 한 건 실패가 다른 건 commit 에 영향을 주지 않는다.
     * 부분 성공 결과는 응답 body 의 data 필드에 포함된다.</p>
     *
     * @param request 일괄 등록 요청
     * @return 처리 결과 (created/skippedDuplicate/failed/errors)
     */
    @PostMapping("/bulk-import")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<BulkImportCatalogItemResult> bulkImport(
            @Valid @RequestBody BulkImportCatalogItemRequest request) {

        BulkImportCatalogItemResult result = bulkImportCatalogItemUseCase.bulkImport(request.toCommand());

        return ApiResponse.of(200, "카탈로그 일괄 등록 처리 완료", result);
    }
}
