package com.gearshow.backend.user.application.port.in;

/**
 * 회원 탈퇴 유스케이스.
 */
public interface WithdrawUseCase {

    /**
     * 인증된 사용자를 탈퇴 처리한다.
     * ACTIVE 상태에서만 가능하며, 소유 쇼케이스는 DELETED 처리된다.
     *
     * @param userId 사용자 ID
     */
    void withdraw(Long userId);
}
