package com.gearshow.backend.showcase.adapter.out.storage.s3.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * S3 이미지 다운로드 실패 시 발생하는 예외.
 */
public class S3DownloadFailedException extends CustomException {

    public S3DownloadFailedException() {
        super(ErrorCode.STORAGE_DOWNLOAD_FAILED);
    }
}
