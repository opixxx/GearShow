package com.gearshow.backend.user.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.user.adapter.in.web.dto.LoginResponse;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.port.in.DevLoginUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 환경 전용 인증 컨트롤러.
 * OAuth 없이 테스트 사용자로 즉시 로그인할 수 있다.
 * dev 프로파일에서만 활성화된다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final DevLoginUseCase devLoginUseCase;

    /**
     * 개발용 로그인. OAuth 인증 없이 테스트 사용자로 JWT를 발급한다.
     */
    @PostMapping("/dev-login")
    public ApiResponse<LoginResponse> devLogin() {
        LoginResult result = devLoginUseCase.devLogin();
        return ApiResponse.of(200, "개발용 로그인 성공", LoginResponse.from(result));
    }
}
