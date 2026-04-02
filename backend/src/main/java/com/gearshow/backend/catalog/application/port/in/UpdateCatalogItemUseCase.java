package com.gearshow.backend.catalog.application.port.in;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.application.dto.UpdateCatalogItemCommand;

/**
 * 카탈로그 아이템 수정 유스케이스.
 */
public interface UpdateCatalogItemUseCase {

    /**
     * 카탈로그 아이템을 수정한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @param command       수정 커맨드
     * @return 수정된 카탈로그 아이템 상세
     */
    CatalogItemDetailResult update(Long catalogItemId, UpdateCatalogItemCommand command);
}
