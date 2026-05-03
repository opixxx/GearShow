package com.gearshow.backend.admin.application.port.in;

import com.gearshow.backend.admin.application.dto.ChangeAdminPasswordCommand;

/**
 * 관리자 비밀번호 변경 유스케이스.
 *
 * <p>인증된 admin 본인의 비밀번호를 회전한다. 환경변수로 부트스트랩된 임시 비밀번호를 영구
 * 비밀번호로 교체하는 운영 절차를 위한 엔드포인트의 진입점.</p>
 */
public interface ChangeAdminPasswordUseCase {

    void change(ChangeAdminPasswordCommand command);
}
