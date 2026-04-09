package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 댓글을 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundShowcaseCommentException extends CustomException {

    public NotFoundShowcaseCommentException() {
        super(ErrorCode.SHOWCASE_COMMENT_NOT_FOUND);
    }
}
