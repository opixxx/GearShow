package com.gearshow.backend.user.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.user.adapter.in.web.dto.*;
import jakarta.validation.Valid;
import com.gearshow.backend.user.application.dto.MyProfileResult;
import com.gearshow.backend.user.application.dto.UpdateProfileResult;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetMyProfileUseCase;
import com.gearshow.backend.user.application.port.in.GetUserProfileUseCase;
import com.gearshow.backend.user.application.port.in.UpdateProfileUseCase;
import com.gearshow.backend.user.application.port.in.WithdrawUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final GetMyProfileUseCase getMyProfileUseCase;
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final WithdrawUseCase withdrawUseCase;

    /**
     * 내 프로필을 조회한다.
     *
     * @param userId 인증된 사용자 ID
     * @return 내 프로필 정보
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId) {

        MyProfileResult result = getMyProfileUseCase.getMyProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "프로필 조회 성공", MyProfileResponse.from(result)));
    }

    /**
     * 다른 사용자의 공개 프로필을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 공개 프로필 정보
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable Long userId) {

        UserProfileResult result = getUserProfileUseCase.getUserProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "프로필 조회 성공", UserProfileResponse.from(result)));
    }

    /**
     * 내 프로필을 수정한다.
     *
     * @param userId  인증된 사용자 ID
     * @param request 수정할 프로필 정보
     * @return 수정된 프로필 정보
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UpdateProfileResult result = updateProfileUseCase.updateProfile(
                userId, request.toCommand());

        return ResponseEntity.ok(
                ApiResponse.of(200, "프로필 수정 성공", UpdateProfileResponse.from(result)));
    }

    /**
     * 회원 탈퇴한다.
     *
     * @param userId 인증된 사용자 ID
     * @return 탈퇴 결과
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId) {

        withdrawUseCase.withdraw(userId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "회원 탈퇴 성공"));
    }
}
