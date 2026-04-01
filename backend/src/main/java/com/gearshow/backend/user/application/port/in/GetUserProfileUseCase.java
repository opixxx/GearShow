package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.UserProfileResult;

/**
 * 다른 사용자 프로필 조회 유스케이스.
 */
public interface GetUserProfileUseCase {

    /**
     * 사용자 ID로 공개 프로필을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 공개 프로필 정보
     */
    UserProfileResult getUserProfile(Long userId);
}
