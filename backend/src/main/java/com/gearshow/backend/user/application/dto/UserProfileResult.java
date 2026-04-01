package com.gearshow.backend.user.application.dto;

import com.gearshow.backend.user.domain.model.User;

/**
 * 다른 사용자 공개 프로필 조회 결과.
 *
 * @param userId          사용자 ID
 * @param nickname        닉네임
 * @param profileImageUrl 프로필 이미지 URL
 */
public record UserProfileResult(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    /**
     * 도메인 모델로부터 결과를 생성한다.
     *
     * @param user 사용자 도메인 모델
     * @return 공개 프로필 결과
     */
    public static UserProfileResult from(User user) {
        return new UserProfileResult(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
