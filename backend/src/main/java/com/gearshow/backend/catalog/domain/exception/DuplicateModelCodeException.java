package com.gearshow.backend.catalog.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 동일 카테고리 내 모델 코드 중복 시 발생하는 예외.
 */
public class DuplicateModelCodeException extends CustomException {

    public DuplicateModelCodeException() {
        super(ErrorCode.CATALOG_ITEM_DUPLICATE_MODEL_CODE);
    }
}
