package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 이미지 정렬 순서가 중복되었을 때 발생하는 예외.
 */
public class DuplicateSortOrderException extends CustomException {

    public DuplicateSortOrderException() {
        super(ErrorCode.SHOWCASE_IMAGE_DUPLICATE_SORT_ORDER);
    }
}
