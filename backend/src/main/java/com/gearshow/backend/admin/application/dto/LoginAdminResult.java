package com.gearshow.backend.admin.application.dto;

/**
 * 관리자 로그인 결과.
 *
 * @param adminId      로그인된 관리자 ID
 * @param accessToken  Access JWT (type=ADMIN claim 포함)
 * @param refreshToken Refresh JWT
 */
public record LoginAdminResult(
        Long adminId,
        String accessToken,
        String refreshToken
) {
}
