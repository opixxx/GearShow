package com.gearshow.backend.user.application.dto;

import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.UserStatus;

import java.time.Instant;

/**
 * 내 프로필 조회 결과.
 *
 * @param userId          사용자 ID
 * @param nickname        닉네임
 * @param profileImageUrl 프로필 이미지 URL
 * @param phoneNumber     전화번호
 * @param phoneVerified   전화번호 인증 여부
 * @param userStatus      사용자 상태
 * @param createdAt       가입일시
 */
public record MyProfileResult(
        Long userId,
        String nickname,
        String profileImageUrl,
        String phoneNumber,
        boolean phoneVerified,
        UserStatus userStatus,
        Instant createdAt
) {

    /**
     * 도메인 모델로부터 결과를 생성한다.
     *
     * @param user 사용자 도메인 모델
     * @return 내 프로필 결과
     */
    public static MyProfileResult from(User user) {
        return new MyProfileResult(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getPhoneNumber(),
                user.isPhoneVerified(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
