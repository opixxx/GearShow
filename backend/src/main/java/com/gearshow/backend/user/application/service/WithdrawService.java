package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.port.in.WithdrawUseCase;
import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import com.gearshow.backend.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 유스케이스 구현체.
 * 사용자를 WITHDRAWN 상태로 변경하고 Refresh Token을 삭제한다.
 * TODO: Showcase DELETED 처리, ChatRoom CLOSED 처리는 해당 도메인 구현 시 추가
 */
@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

    private final UserPort userPort;
    private final RefreshTokenPort refreshTokenPort;

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userPort.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        User withdrawn = user.withdraw();
        userPort.save(withdrawn);
        refreshTokenPort.deleteByUserId(userId);
    }
}
