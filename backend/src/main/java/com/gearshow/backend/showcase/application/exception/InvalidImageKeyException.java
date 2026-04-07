package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * S3에 존재하지 않는 이미지 키가 전달된 경우 발생하는 예외.
 * 클라이언트가 Presigned URL로 실제 업로드를 완료하지 않은 채 키를 전달한 경우에 해당한다.
 */
public class InvalidImageKeyException extends CustomException {

    public InvalidImageKeyException() {
        super(ErrorCode.STORAGE_KEY_NOT_FOUND);
    }
}
