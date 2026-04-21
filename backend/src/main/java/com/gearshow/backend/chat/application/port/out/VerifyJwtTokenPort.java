package com.gearshow.backend.chat.application.port.out;

import java.util.Optional;

/**
 * JWT Access Token 을 검증해 사용자 ID 를 돌려주는 아웃바운드 포트.
 *
 * <p>chat 컨텍스트가 user 컨텍스트의 인프라({@code JwtTokenProvider}) 를 직접 참조하지 않도록
 * 이 포트를 경유한다. 구현체는 chat 어댑터 계층에 위치하며 user 컨텍스트의 JWT 검증 컴포넌트를
 * 래핑한다.</p>
 *
 * <p>검증 실패(만료·서명 불일치·파싱 오류 등)는 예외 대신 {@link Optional#empty()} 로 표현한다.
 * 호출부가 실패 경로를 누락하는 것을 컴파일러가 잡도록 하기 위함이다.</p>
 */
public interface VerifyJwtTokenPort {

    /**
     * Access Token 을 검증해 사용자 ID 를 반환한다.
     *
     * @param accessToken 검증할 JWT 문자열. {@code null} 이거나 공백 허용 (내부에서 empty 반환).
     * @return 유효한 토큰이면 사용자 ID, 그 외는 {@link Optional#empty()}
     */
    Optional<Long> resolveUserId(String accessToken);
}
