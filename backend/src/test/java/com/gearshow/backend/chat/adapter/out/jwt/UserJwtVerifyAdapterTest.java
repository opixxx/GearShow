package com.gearshow.backend.chat.adapter.out.jwt;

import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserJwtVerifyAdapterTest {

    @InjectMocks
    private UserJwtVerifyAdapter adapter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("유효한 토큰은 사용자 ID를 반환한다")
    void resolveUserId_validToken_returnsUserId() {
        // given
        String token = "valid-token";
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUserId(token)).willReturn(42L);

        // when
        Optional<Long> result = adapter.resolveUserId(token);

        // then
        assertThat(result).contains(42L);
    }

    @Test
    @DisplayName("null 토큰은 Optional.empty 를 반환하며 검증을 호출하지 않는다")
    void resolveUserId_nullToken_returnsEmpty() {
        // when
        Optional<Long> result = adapter.resolveUserId(null);

        // then
        assertThat(result).isEmpty();
        verify(jwtTokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("공백 토큰은 Optional.empty 를 반환하며 검증을 호출하지 않는다")
    void resolveUserId_blankToken_returnsEmpty() {
        // when
        Optional<Long> result = adapter.resolveUserId("   ");

        // then
        assertThat(result).isEmpty();
        verify(jwtTokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("유효하지 않은 토큰은 Optional.empty 를 반환한다")
    void resolveUserId_invalidToken_returnsEmpty() {
        // given
        String token = "invalid-token";
        given(jwtTokenProvider.validateToken(token)).willReturn(false);

        // when
        Optional<Long> result = adapter.resolveUserId(token);

        // then
        assertThat(result).isEmpty();
        verify(jwtTokenProvider, never()).getUserId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("getUserId 에서 예외가 발생해도 Optional.empty 를 반환한다 (호출부 보호)")
    void resolveUserId_getUserIdThrows_returnsEmpty() {
        // given
        String token = "broken-token";
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUserId(token))
                .willThrow(new NumberFormatException("subject not a number"));

        // when
        Optional<Long> result = adapter.resolveUserId(token);

        // then
        assertThat(result).isEmpty();
    }
}
