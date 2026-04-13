package com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception;

import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Tripo API 영구 실패 예외 (재시도 무의미).
 *
 * <p>다음 에러 코드에 해당:</p>
 * <ul>
 *   <li>401 / 1002: Authentication failed → API Key 교체 필요</li>
 *   <li>400 / 1003, 1004: Request body/parameter invalid → 코드 버그</li>
 *   <li>403 / 1005: Insufficient permission</li>
 *   <li>403 / 2010: Not enough credit → 크레딧 충전 필요</li>
 *   <li>400 / 2003, 2004: Image file empty/unsupported</li>
 *   <li>400 / 2008: Content policy violation</li>
 * </ul>
 *
 * <p>이 예외가 발생하면 모델은 즉시 FAILED 로 전환된다.
 * 크레딧 부족(2010)과 인증 실패(1002)는 추가로 Alert 를 발송해야 한다.</p>
 */
public class TripoNonRetryableException extends TripoApiException {

    /** 개발자 Alert 가 필요한 에러인지 여부. 크레딧 부족, 인증 실패 시 true. */
    private final boolean alertRequired;

    public TripoNonRetryableException(ErrorCode errorCode, boolean alertRequired) {
        super(errorCode);
        this.alertRequired = alertRequired;
    }

    public TripoNonRetryableException(ErrorCode errorCode) {
        this(errorCode, false);
    }

    public boolean isAlertRequired() {
        return alertRequired;
    }
}
