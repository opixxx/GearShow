package com.gearshow.backend.user.adapter.in.web.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * 소셜 로그인 요청 DTO.
 *
 * @param authorizationCode 소셜 인가 코드
 * @param accessToken       소셜 SDK에서 받은 액세스 토큰
 */
public record LoginRequest(
        String authorizationCode,
        String accessToken
) {

    /**
     * 인가 코드 또는 액세스 토큰 중 하나는 반드시 제공되어야 한다.
     */
    @AssertTrue(message = "인가 코드 또는 액세스 토큰 중 하나는 필수입니다")
    private boolean isCredentialProvided() {
        boolean hasCode = authorizationCode != null && !authorizationCode.isBlank();
        boolean hasToken = accessToken != null && !accessToken.isBlank();
        return hasCode || hasToken;
    }
}
