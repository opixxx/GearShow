package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 댓글 도메인 규칙을 위반했을 때 발생하는 예외.
 */
public class InvalidShowcaseCommentException extends CustomException {

    public InvalidShowcaseCommentException() {
        super(ErrorCode.SHOWCASE_COMMENT_INVALID);
    }
}
