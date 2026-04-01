package com.gearshow.backend.user.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Refresh Token 저장소 인터페이스.
 */
public interface RefreshTokenPort {

    /**
     * Refresh Token을 저장한다.
     *
     * @param userId    사용자 ID
     * @param token     토큰 문자열
     * @param expiresAt 만료 시각
     */
    void save(Long userId, String token, LocalDateTime expiresAt);

    /**
     * 토큰 문자열로 사용자 ID를 조회한다.
     *
     * @param token 토큰 문자열
     * @return 사용자 ID Optional
     */
    Optional<Long> findUserIdByToken(String token);

    /**
     * 사용자 ID로 Refresh Token을 삭제한다.
     *
     * @param userId 사용자 ID
     */
    void deleteByUserId(Long userId);
}
