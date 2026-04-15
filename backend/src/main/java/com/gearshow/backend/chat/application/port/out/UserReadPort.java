package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.application.dto.UserProfile;

import java.util.List;
import java.util.Map;

/**
 * 유저 프로필 요약을 chat BC로 가져오기 위한 읽기 전용 포트.
 *
 * <p>chat 패키지는 user 도메인 타입을 절대 import하지 않는다.</p>
 */
public interface UserReadPort {

    UserProfile getProfile(Long userId);

    /**
     * 복수 유저 프로필 일괄 조회 (N+1 회피).
     *
     * @return userId → profile 매핑
     */
    Map<Long, UserProfile> getProfiles(List<Long> userIds);
}
