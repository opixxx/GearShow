package com.gearshow.backend.user.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 생성, 검증, 파싱을 담당하는 컴포넌트.
 *
 * <p>JWT claim 에 {@code type} 필드를 포함하여 일반 사용자({@code USER}) 와 관리자({@code ADMIN})
 * 를 구분한다. {@code type} claim 이 없는 기존 토큰은 호환성 유지를 위해 {@code USER} 로 간주된다.</p>
 */
@Component
public class JwtTokenProvider {

    /** JWT subject type — 일반 사용자 */
    public static final String TYPE_USER = "USER";

    /** JWT subject type — 관리자 */
    public static final String TYPE_ADMIN = "ADMIN";

    private static final String CLAIM_TYPE = "type";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * 일반 사용자 Access Token 을 생성한다 ({@code type=USER}).
     */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, TYPE_USER, accessTokenExpirationMs);
    }

    /**
     * 일반 사용자 Refresh Token 을 생성한다 ({@code type=USER}).
     */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, TYPE_USER, refreshTokenExpirationMs);
    }

    /**
     * 지정한 type 으로 Access Token 을 생성한다.
     */
    public String generateAccessToken(Long subjectId, String type) {
        return generateToken(subjectId, type, accessTokenExpirationMs);
    }

    /**
     * 지정한 type 으로 Refresh Token 을 생성한다.
     */
    public String generateRefreshToken(Long subjectId, String type) {
        return generateToken(subjectId, type, refreshTokenExpirationMs);
    }

    /**
     * 토큰에서 subject id 를 추출한다 (USER 의 userId 또는 ADMIN 의 adminId).
     */
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰에서 subject type 을 추출한다.
     *
     * <p>{@code type} claim 이 없는 기존 토큰은 호환성을 위해 {@link #TYPE_USER} 를 반환한다.</p>
     */
    public String getType(String token) {
        Claims claims = parseClaims(token);
        Object type = claims.get(CLAIM_TYPE);
        return type == null ? TYPE_USER : type.toString();
    }

    /**
     * 토큰의 유효성을 검증한다.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Access Token 의 만료 시간(초) 을 반환한다.
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    private String generateToken(Long subjectId, String type, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
