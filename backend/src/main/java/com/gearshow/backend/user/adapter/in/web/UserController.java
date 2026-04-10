package com.gearshow.backend.user.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.user.adapter.in.web.dto.*;
import com.gearshow.backend.user.application.dto.MyProfileResult;
import com.gearshow.backend.user.application.dto.UpdateProfileCommand;
import com.gearshow.backend.user.application.dto.UpdateProfileResult;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.CheckNicknameUseCase;
import com.gearshow.backend.user.application.port.in.GetMyProfileUseCase;
import com.gearshow.backend.user.application.port.in.GetUserProfileUseCase;
import com.gearshow.backend.user.application.port.in.UpdateProfileUseCase;
import com.gearshow.backend.user.application.port.in.WithdrawUseCase;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 사용자 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final CheckNicknameUseCase checkNicknameUseCase;
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final WithdrawUseCase withdrawUseCase;

    /**
     * 닉네임 중복 여부를 확인한다.
     *
     * @param nickname 확인할 닉네임
     * @return 닉네임 사용 가능 여부
     */
    @GetMapping("/nicknames/check")
    public ApiResponse<CheckNicknameResponse> checkNickname(
            @RequestParam String nickname) {

        boolean available = checkNicknameUseCase.isAvailable(nickname);

        String message = available ? "사용 가능한 닉네임입니다" : "이미 사용 중인 닉네임입니다";
        return ApiResponse.of(200, message, new CheckNicknameResponse(nickname, available));
    }

    /**
     * 내 프로필을 조회한다.
     *
     * @param userId 인증된 사용자 ID
     * @return 내 프로필 정보
     */
    @GetMapping("/me")
    public ApiResponse<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal Long userId) {

        MyProfileResult result = getMyProfileUseCase.getMyProfile(userId);

        return ApiResponse.of(200, "프로필 조회 성공", MyProfileResponse.from(result));
    }

    /**
     * 다른 사용자의 공개 프로필을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 공개 프로필 정보
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getUserProfile(
            @PathVariable Long userId) {

        UserProfileResult result = getUserProfileUseCase.getUserProfile(userId);

        return ApiResponse.of(200, "프로필 조회 성공", UserProfileResponse.from(result));
    }

    /**
     * 내 프로필을 수정한다.
     * Multipart 요청으로 닉네임과 프로필 이미지를 함께 수정할 수 있다.
     *
     * @param userId       인증된 사용자 ID
     * @param nickname     변경할 닉네임 (선택)
     * @param profileImage 변경할 프로필 이미지 (선택)
     * @return 수정된 프로필 정보
     */
    @PatchMapping(value = "/me", consumes = "multipart/form-data")
    public ApiResponse<UpdateProfileResponse> updateProfile(
            @AuthenticationPrincipal Long userId,
            @RequestPart(required = false)
            @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
            String nickname,
            @RequestPart(required = false) MultipartFile profileImage) throws IOException {

        UpdateProfileCommand command = new UpdateProfileCommand(
                nickname,
                profileImage != null ? profileImage.getBytes() : null,
                profileImage != null ? profileImage.getContentType() : null,
                profileImage != null ? profileImage.getOriginalFilename() : null
        );

        UpdateProfileResult result = updateProfileUseCase.updateProfile(userId, command);

        return ApiResponse.of(200, "프로필 수정 성공", UpdateProfileResponse.from(result));
    }

    /**
     * 회원 탈퇴한다.
     *
     * @param userId 인증된 사용자 ID
     * @return 탈퇴 결과
     */
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long userId) {

        withdrawUseCase.withdraw(userId);

        return ApiResponse.of(200, "회원 탈퇴 성공");
    }
}
