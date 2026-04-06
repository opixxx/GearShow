package com.gearshow.backend.showcase.adapter.out.storage.s3.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * S3 Presigned URL 생성 실패 시 발생하는 예외.
 */
public class S3PresignFailedException extends CustomException {

    public S3PresignFailedException() {
        super(ErrorCode.STORAGE_PRESIGN_FAILED);
    }
}
