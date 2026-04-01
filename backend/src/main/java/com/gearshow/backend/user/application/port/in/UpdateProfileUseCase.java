package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.UpdateProfileCommand;
import com.gearshow.backend.user.application.dto.UpdateProfileResult;

/**
 * 프로필 수정 유스케이스.
 */
public interface UpdateProfileUseCase {

    /**
     * 인증된 사용자의 프로필을 수정한다.
     *
     * @param userId  사용자 ID
     * @param command 수정할 프로필 정보
     * @return 수정된 프로필 정보
     */
    UpdateProfileResult updateProfile(Long userId, UpdateProfileCommand command);
}
