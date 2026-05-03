package com.gearshow.backend.admin.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 관리자 자격증명 실패 예외 (미존재 email 또는 비밀번호 불일치).
 *
 * <p>OWASP 권고에 따라 두 케이스를 구분하지 않고 동일 예외로 통합한다.
 * user enumeration 공격(존재하는 admin email 을 응답 코드/메시지로 식별) 을 차단하기 위함이다.
 * 내부 로그에서는 둘을 구분해 기록할 수 있으나, 응답에는 동일 메시지/코드 가 반환된다.</p>
 */
public class InvalidCredentialsException extends CustomException {

    public InvalidCredentialsException() {
        super(ErrorCode.ADMIN_INVALID_CREDENTIALS);
    }
}
