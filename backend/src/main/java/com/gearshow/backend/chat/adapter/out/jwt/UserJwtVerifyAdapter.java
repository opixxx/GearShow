package com.gearshow.backend.chat.adapter.out.jwt;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gearshow.backend.chat.application.port.out.VerifyJwtTokenPort;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link VerifyJwtTokenPort} 의 구현체. user 컨텍스트의 {@link JwtTokenProvider} 를
 * 래핑해 chat 컨텍스트의 아웃바운드 포트로 노출한다.
 *
 * <p>chat 패키지 내에서 user 인프라에 대한 의존은 오직 이 어댑터 한 곳으로 국한된다.
 * ArchUnit 규칙이 {@code chat/**} → {@code user.infrastructure.**} 경로를 제한할 때
 * 이 파일(chat/adapter/out/jwt)만 예외 경계로 동작한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserJwtVerifyAdapter implements VerifyJwtTokenPort {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Optional<Long> resolveUserId(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return Optional.empty();
        }
        if (!jwtTokenProvider.validateToken(accessToken)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jwtTokenProvider.getUserId(accessToken));
        } catch (RuntimeException ex) {
            log.debug("JWT 사용자 ID 파싱 실패", ex);
            return Optional.empty();
        }
    }
}
