package com.gearshow.backend.user.adapter.in.web.dto;

import com.gearshow.backend.user.application.dto.MyProfileResult;
import com.gearshow.backend.user.domain.vo.UserStatus;

import java.time.Instant;

/**
 * 내 프로필 조회 응답 DTO.
 */
public record MyProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        String phoneNumber,
        boolean isPhoneVerified,
        UserStatus userStatus,
        Instant createdAt
) {

    public static MyProfileResponse from(MyProfileResult result) {
        return new MyProfileResponse(
                result.userId(),
                result.nickname(),
                result.profileImageUrl(),
                result.phoneNumber(),
                result.phoneVerified(),
                result.userStatus(),
                result.createdAt()
        );
    }
}
