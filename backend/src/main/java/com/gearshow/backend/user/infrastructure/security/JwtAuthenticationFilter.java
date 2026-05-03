package com.gearshow.backend.user.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * JWT 인증 필터.
 *
 * <p>요청 헤더의 Bearer 토큰을 검증하고 SecurityContext 에 인증 정보를 설정한다.
 * 토큰의 {@code type} claim 에 따라 {@code ROLE_USER} 또는 {@code ROLE_ADMIN} authority 를 부여한다.
 * {@code type} claim 이 없는 기존 토큰은 호환성을 위해 {@code ROLE_USER} 로 처리된다.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> ALLOWED_TYPES = Set.of(
            JwtTokenProvider.TYPE_USER, JwtTokenProvider.TYPE_ADMIN);

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String type = jwtTokenProvider.getType(token);
            // 호환성(ADR-014 D3): type claim 없는 기존 토큰 또는 mock 환경은 USER 로 처리.
            if (type == null) {
                type = JwtTokenProvider.TYPE_USER;
            }
            // type 화이트리스트 검증 — secret 유출 시 임의 type 으로 임의 ROLE 부여 방지.
            if (ALLOWED_TYPES.contains(type)) {
                Long subjectId = jwtTokenProvider.getUserId(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subjectId,
                                null,
                                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + type)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
