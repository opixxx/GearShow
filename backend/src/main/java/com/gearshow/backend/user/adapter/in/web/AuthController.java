package com.gearshow.backend.user.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.user.adapter.in.web.dto.LoginRequest;
import com.gearshow.backend.user.adapter.in.web.dto.LoginResponse;
import com.gearshow.backend.user.adapter.in.web.dto.RefreshTokenRequest;
import com.gearshow.backend.user.application.dto.LoginCommand;
import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.dto.RefreshTokenCommand;
import com.gearshow.backend.user.application.port.in.LoginUseCase;
import com.gearshow.backend.user.application.port.in.LogoutUseCase;
import com.gearshow.backend.user.application.port.in.RefreshTokenUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;

    /**
     * 소셜 로그인을 수행한다.
     *
     * @param provider 소셜 로그인 제공자 (kakao, apple)
     * @param request  로그인 요청
     * @return JWT 토큰
     */
    @PostMapping("/login/{provider}")
    public ApiResponse<LoginResponse> login(
            @PathVariable String provider,
            @Valid @RequestBody LoginRequest request) {

        LoginCommand command = new LoginCommand(
                provider,
                request.authorizationCode(),
                request.accessToken());
        LoginResult result = loginUseCase.login(command);

        return ApiResponse.of(200, "로그인 성공", LoginResponse.from(result));
    }

    /**
     * 토큰을 갱신한다.
     *
     * @param request 토큰 갱신 요청
     * @return 새로운 JWT 토큰
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        RefreshTokenCommand command = new RefreshTokenCommand(request.refreshToken());
        LoginResult result = refreshTokenUseCase.refresh(command);

        return ApiResponse.of(200, "토큰 갱신 성공", LoginResponse.from(result));
    }

    /**
     * 로그아웃한다. 현재 사용자의 Refresh Token을 무효화한다.
     *
     * @param userId 인증된 사용자 ID
     * @return 로그아웃 결과
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Long userId) {

        logoutUseCase.logout(userId);

        return ApiResponse.of(200, "로그아웃 성공");
    }
}
