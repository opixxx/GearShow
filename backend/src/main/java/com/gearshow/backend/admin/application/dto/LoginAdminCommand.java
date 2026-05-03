package com.gearshow.backend.admin.application.dto;

/**
 * 관리자 로그인 커맨드.
 *
 * <p>{@link #toString()} 은 password 를 마스킹한다 — 우발적 로그 직렬화 시 평문 유출 차단.</p>
 */
public record LoginAdminCommand(
        String email,
        String password
) {

    @Override
    public String toString() {
        return "LoginAdminCommand[email=" + email + ", password=***]";
    }
}
