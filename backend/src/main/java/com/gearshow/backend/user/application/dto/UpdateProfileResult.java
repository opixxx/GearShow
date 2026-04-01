package com.gearshow.backend.user.application.dto;

import com.gearshow.backend.user.domain.model.User;

/**
 * 프로필 수정 결과.
 *
 * @param userId          사용자 ID
 * @param nickname        수정된 닉네임
 * @param profileImageUrl 수정된 프로필 이미지 URL
 */
public record UpdateProfileResult(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    /**
     * 도메인 모델로부터 결과를 생성한다.
     *
     * @param user 사용자 도메인 모델
     * @return 프로필 수정 결과
     */
    public static UpdateProfileResult from(User user) {
        return new UpdateProfileResult(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
