package com.gearshow.backend.user.adapter.in.web;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.user.adapter.in.web.dto.LoginResponse;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.port.in.DevLoginUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 개발 환경 전용 인증 컨트롤러.
 * OAuth 없이 테스트 사용자로 즉시 로그인할 수 있다.
 *
 * <p>{@code local}, {@code dev}, {@code test} 프로파일에서만 Bean 으로 등록되며
 * 프로덕션 환경에는 노출되지 않는다. 프로파일 가드를 제거하면 {@code /api/v1/auth/dev-login}
 * 엔드포인트를 통해 임의 사용자 JWT 를 발급할 수 있어 즉시 권한 상승 취약점이 된다.</p>
 */
@Profile({"local", "dev", "test"})
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final DevLoginUseCase devLoginUseCase;

    /**
     * 개발용 로그인. OAuth 인증 없이 테스트 사용자로 JWT를 발급한다.
     */
    @PostMapping("/dev-login")
    public ApiResponse<LoginResponse> devLogin(
            @RequestParam(required = false) Long userId) {
        LoginResult result = userId != null
                ? devLoginUseCase.devLoginAs(userId)
                : devLoginUseCase.devLogin();
        return ApiResponse.of(200, "개발용 로그인 성공", LoginResponse.from(result));
    }
}
