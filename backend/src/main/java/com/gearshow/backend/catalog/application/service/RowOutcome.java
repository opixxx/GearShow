package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult.BulkImportError;

/**
 * 단건 처리 결과.
 *
 * <p>{@link BulkImportRowProcessor} 와 {@link BulkImportCatalogItemService} 간 통신 DTO.
 * 일괄 등록 카운트 집계의 단위가 된다.</p>
 *
 * @param status CREATED 일 땐 error 가 null, 그 외에는 error 가 채워진다.
 * @param error  실패/중복 상세 (CREATED 일 땐 null)
 */
record RowOutcome(
        Status status,
        BulkImportError error
) {

    enum Status {
        CREATED,
        DUPLICATE,
        FAILED
    }

    static RowOutcome created() {
        return new RowOutcome(Status.CREATED, null);
    }

    static RowOutcome duplicate(BulkImportError error) {
        return new RowOutcome(Status.DUPLICATE, error);
    }

    static RowOutcome failed(BulkImportError error) {
        return new RowOutcome(Status.FAILED, error);
    }
}
