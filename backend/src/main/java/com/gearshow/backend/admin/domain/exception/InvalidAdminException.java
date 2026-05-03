package com.gearshow.backend.admin.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 관리자 도메인 규칙을 위반했을 때 발생하는 예외 (예: blank email/password).
 */
public class InvalidAdminException extends CustomException {

    public InvalidAdminException() {
        super(ErrorCode.ADMIN_INVALID_INPUT);
    }
}
