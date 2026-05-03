package com.gearshow.backend.admin.adapter.in.web;

import com.gearshow.backend.admin.adapter.in.web.dto.ChangeAdminPasswordRequest;
import com.gearshow.backend.admin.application.port.in.ChangeAdminPasswordUseCase;
import com.gearshow.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 본인 계정 관리 API.
 *
 * <p>{@code /api/admin/me/**} 경로는 SecurityConfig 룰에 의해 ADMIN 권한 토큰만 접근 가능하다.
 * 컨트롤러는 SecurityContext 의 {@link Authentication#getPrincipal()} 에서 adminId 를 추출한다
 * (JwtAuthenticationFilter 가 token sub 를 principal 로 설정).</p>
 */
@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
@Validated
public class AdminMeController {

    private final ChangeAdminPasswordUseCase changeAdminPasswordUseCase;

    /**
     * 인증된 admin 본인의 비밀번호를 변경한다.
     *
     * <p>환경변수로 부트스트랩된 임시 비밀번호를 영구 비밀번호로 회전하기 위한 엔드포인트.
     * 첫 로그인 후 즉시 호출 → 환경변수 제거 권장.</p>
     */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangeAdminPasswordRequest request) {

        Long adminId = (Long) authentication.getPrincipal();
        changeAdminPasswordUseCase.change(request.toCommand(adminId));

        return ApiResponse.of(200, "비밀번호가 변경되었습니다");
    }
}
