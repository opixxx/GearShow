package com.gearshow.backend.admin.application.port.out;

/**
 * 비밀번호 해시/검증 포트.
 *
 * <p>구현체는 BCrypt 등 단방향 해시를 사용한다. application 계층은 어떤 알고리즘을 쓰는지 알지 못한다.</p>
 */
public interface PasswordEncoderPort {

    /**
     * 평문 비밀번호를 해시한다.
     */
    String encode(String rawPassword);

    /**
     * 평문 비밀번호가 해시와 일치하는지 검증한다.
     */
    boolean matches(String rawPassword, String passwordHash);
}
