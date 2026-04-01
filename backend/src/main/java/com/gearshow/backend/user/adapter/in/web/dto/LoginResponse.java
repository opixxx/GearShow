package com.gearshow.backend.user.adapter.in.web.dto;

import com.gearshow.backend.user.application.dto.LoginResult;

/**
 * 로그인/토큰 갱신 응답 DTO.
 *
 * @param accessToken  액세스 토큰
 * @param refreshToken 리프레시 토큰
 * @param tokenType    토큰 타입
 * @param expiresIn    만료 시간(초)
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    /**
     * LoginResult로부터 응답을 생성한다.
     *
     * @param result 로그인 결과
     * @return 로그인 응답
     */
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn()
        );
    }
}
