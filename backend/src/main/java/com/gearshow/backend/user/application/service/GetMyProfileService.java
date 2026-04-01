package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.MyProfileResult;
import com.gearshow.backend.user.application.port.in.GetMyProfileUseCase;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import com.gearshow.backend.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class GetMyProfileService implements GetMyProfileUseCase {

    private final UserPort userPort;

    @Override
    @Transactional(readOnly = true)
    public MyProfileResult getMyProfile(Long userId) {
        User user = userPort.findById(userId)
                .orElseThrow(NotFoundUserException::new);
        return MyProfileResult.from(user);
    }
}
