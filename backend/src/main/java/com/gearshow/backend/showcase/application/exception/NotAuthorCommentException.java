package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 댓글 작성자가 아닌 경우 발생하는 예외.
 */
public class NotAuthorCommentException extends CustomException {

    public NotAuthorCommentException() {
        super(ErrorCode.SHOWCASE_COMMENT_NOT_AUTHOR);
    }
}
