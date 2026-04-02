package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 재정렬 요청의 이미지 목록이 실제 쇼케이스 이미지 목록과 일치하지 않을 때 발생하는 예외.
 */
public class ImageReorderMismatchException extends CustomException {

    public ImageReorderMismatchException() {
        super(ErrorCode.SHOWCASE_IMAGE_REORDER_MISMATCH);
    }
}
