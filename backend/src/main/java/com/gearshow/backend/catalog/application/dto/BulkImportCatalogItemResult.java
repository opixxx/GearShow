package com.gearshow.backend.catalog.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;

import java.util.List;

/**
 * 카탈로그 아이템 일괄 등록 결과.
 *
 * <p>created + skippedDuplicate + failed 의 합은 입력된 items 수와 같다.
 * errors[] 에는 skippedDuplicate 와 failed 항목의 상세 정보가 누적되며,
 * created 항목은 errors 에 포함되지 않는다.</p>
 */
public record BulkImportCatalogItemResult(
        int created,
        int skippedDuplicate,
        int failed,
        List<BulkImportError> errors
) {

    /**
     * 일괄 등록 실패/중복 항목 상세.
     *
     * @param rowIndex   입력 리스트 내 0-based 인덱스
     * @param category   요청 카테고리 (요청 자체가 깨진 경우 null 가능)
     * @param brand      요청 브랜드
     * @param modelCode  요청 모델 코드 (nullable)
     * @param errorCode  ErrorCode.name() (예: CATALOG_ITEM_DUPLICATE_MODEL_CODE, CATALOG_ITEM_INVALID, INTERNAL_ERROR)
     * @param message    사용자에게 노출할 한글 메시지
     */
    public record BulkImportError(
            int rowIndex,
            Category category,
            String brand,
            String modelCode,
            String errorCode,
            String message
    ) {
    }
}
