package com.gearshow.backend.catalog.application.port.in;

import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemResult;

/**
 * 카탈로그 아이템 일괄 등록 유스케이스.
 *
 * <p>운영자가 손검수한 외부 카탈로그 데이터를 한 번에 적재한다.
 * 각 항목은 독립된 트랜잭션으로 처리되어 한 건의 실패가 다른 건의 commit에 영향을 주지 않는다.</p>
 */
public interface BulkImportCatalogItemUseCase {

    /**
     * 여러 카탈로그 아이템을 일괄 등록한다.
     *
     * @param command 일괄 등록 커맨드
     * @return 처리 결과 (created/skippedDuplicate/failed 카운트와 errors)
     */
    BulkImportCatalogItemResult bulkImport(BulkImportCatalogItemCommand command);
}
