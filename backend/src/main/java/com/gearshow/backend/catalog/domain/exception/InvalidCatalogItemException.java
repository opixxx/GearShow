package com.gearshow.backend.catalog.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 카탈로그 아이템 도메인 규칙을 위반했을 때 발생하는 예외.
 */
public class InvalidCatalogItemException extends CustomException {

    public InvalidCatalogItemException() {
        super(ErrorCode.CATALOG_ITEM_INVALID);
    }
}
