package com.gearshow.backend.catalog.application.port.in;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemResult;

/**
 * 카탈로그 아이템 등록 유스케이스.
 */
public interface CreateCatalogItemUseCase {

    /**
     * 새로운 카탈로그 아이템을 등록한다.
     *
     * @param command 등록 커맨드
     * @return 등록 결과
     */
    CreateCatalogItemResult create(CreateCatalogItemCommand command);
}
