package com.gearshow.backend.user.adapter.in.web.dto;

import com.gearshow.backend.user.application.dto.UpdateProfileResult;

/**
 * 프로필 수정 응답 DTO.
 */
public record UpdateProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    public static UpdateProfileResponse from(UpdateProfileResult result) {
        return new UpdateProfileResponse(
                result.userId(),
                result.nickname(),
                result.profileImageUrl()
        );
    }
}
