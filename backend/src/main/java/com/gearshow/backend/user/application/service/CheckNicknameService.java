package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.port.in.CheckNicknameUseCase;
import com.gearshow.backend.user.application.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임 중복 확인 서비스.
 */
@Service
@RequiredArgsConstructor
public class CheckNicknameService implements CheckNicknameUseCase {

    private final UserPort userPort;

    /**
     * 닉네임 사용 가능 여부를 확인한다.
     * 이미 존재하는 닉네임이면 false를 반환한다.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isAvailable(String nickname) {
        return !userPort.existsByNickname(nickname);
    }
}
