package com.gearshow.backend.user.application.port.in;

/**
 * 닉네임 중복 확인 유스케이스.
 */
public interface CheckNicknameUseCase {

    /**
     * 닉네임 사용 가능 여부를 확인한다.
     *
     * @param nickname 확인할 닉네임
     * @return 사용 가능하면 true
     */
    boolean isAvailable(String nickname);
}
