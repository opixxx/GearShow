package com.gearshow.backend.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 DTO.
 *
 * @param authorizationCode 소셜 인가 코드
 */
public record LoginRequest(
        @NotBlank(message = "인가 코드는 필수입니다")
        String authorizationCode
) {
}
