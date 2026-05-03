package com.gearshow.backend.admin.application.dto;

/**
 * 관리자 비밀번호 변경 커맨드.
 *
 * <p>{@link #toString()} 은 password 들을 마스킹한다 — 우발적 로그 직렬화 시 평문 유출 차단.</p>
 */
public record ChangeAdminPasswordCommand(
        Long adminId,
        String currentPassword,
        String newPassword
) {

    @Override
    @SuppressWarnings("java:S2068") // password=*** 는 credential 이 아니라 마스크 표시 문자열
    public String toString() {
        return "ChangeAdminPasswordCommand[adminId=" + adminId
                + ", currentPassword=***, newPassword=***]";
    }
}
