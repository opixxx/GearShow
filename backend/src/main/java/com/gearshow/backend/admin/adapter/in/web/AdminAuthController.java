package com.gearshow.backend.admin.adapter.in.web;

import com.gearshow.backend.admin.adapter.in.web.dto.AdminLoginRequest;
import com.gearshow.backend.admin.adapter.in.web.dto.AdminLoginResponse;
import com.gearshow.backend.admin.application.dto.LoginAdminResult;
import com.gearshow.backend.admin.application.port.in.LoginAdminUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 인증 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Validated
public class AdminAuthController {

    private final LoginAdminUseCase loginAdminUseCase;

    /**
     * 관리자 로그인.
     *
     * @param request email + password
     * @return Access/Refresh 토큰
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        LoginAdminResult result = loginAdminUseCase.login(request.toCommand());
        return ApiResponse.of(200, "관리자 로그인 성공", AdminLoginResponse.from(result));
    }
}
