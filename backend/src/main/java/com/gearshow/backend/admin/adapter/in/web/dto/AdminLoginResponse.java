package com.gearshow.backend.admin.adapter.in.web.dto;

import com.gearshow.backend.admin.application.dto.LoginAdminResult;

/**
 * 관리자 로그인 응답 DTO.
 */
public record AdminLoginResponse(
        Long adminId,
        String accessToken,
        String refreshToken
) {

    public static AdminLoginResponse from(LoginAdminResult result) {
        return new AdminLoginResponse(result.adminId(), result.accessToken(), result.refreshToken());
    }
}
