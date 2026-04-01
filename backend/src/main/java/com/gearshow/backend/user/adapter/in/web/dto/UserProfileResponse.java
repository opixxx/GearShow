package com.gearshow.backend.user.adapter.in.web.dto;

import com.gearshow.backend.user.application.dto.UserProfileResult;

/**
 * 다른 사용자 공개 프로필 응답 DTO.
 */
public record UserProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
                result.userId(),
                result.nickname(),
                result.profileImageUrl()
        );
    }
}
