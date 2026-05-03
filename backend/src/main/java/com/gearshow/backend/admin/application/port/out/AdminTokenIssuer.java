package com.gearshow.backend.admin.application.port.out;

import com.gearshow.backend.admin.application.dto.LoginAdminResult;

/**
 * 관리자 JWT 발급 포트.
 *
 * <p>구현체는 기존 {@code JwtTokenProvider} 를 재사용하되 claim 에 {@code type=ADMIN} 을 포함시킨다.</p>
 */
public interface AdminTokenIssuer {

    /**
     * adminId 기반으로 Access/Refresh 토큰을 발급한다.
     */
    LoginAdminResult issue(Long adminId);
}
