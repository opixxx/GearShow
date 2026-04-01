package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.MyProfileResult;

/**
 * 내 프로필 조회 유스케이스.
 */
public interface GetMyProfileUseCase {

    /**
     * 인증된 사용자의 프로필을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 내 프로필 정보
     */
    MyProfileResult getMyProfile(Long userId);
}
