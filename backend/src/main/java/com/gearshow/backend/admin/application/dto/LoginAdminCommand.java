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

    /**
     * password 필드를 마스킹하여 반환한다.
     *
     * <p>SonarCloud java:S2068 (Hard-coded credentials) 는 false positive — 본 메서드는
     * 평문 password 를 로그에 노출하지 않기 위한 마스킹이며, "password=***" 리터럴은
     * credential 이 아니라 마스크 표시 문자열이다.</p>
     */
    @Override
    @SuppressWarnings("java:S2068")
    public String toString() {
        return "LoginAdminCommand[email=" + email + ", password=***]";
    }
}
