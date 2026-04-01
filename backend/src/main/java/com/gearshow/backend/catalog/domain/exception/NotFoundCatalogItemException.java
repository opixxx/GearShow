package com.gearshow.backend.catalog.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 카탈로그 아이템을 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundCatalogItemException extends CustomException {

    public NotFoundCatalogItemException() {
        super(ErrorCode.CATALOG_ITEM_NOT_FOUND);
    }
}
