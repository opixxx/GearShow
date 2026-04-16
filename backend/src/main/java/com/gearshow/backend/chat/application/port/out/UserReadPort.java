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
     * 복수 유저 프로필 조회.
     *
     * <p><b>현재 구현 한계</b>: user BC가 아직 batch UseCase를 제공하지 않아 어댑터에서 N회 개별 호출.
     * 채팅방 목록 페이지 크기(≤100) 안에서 동작하지만 진정한 N+1 회피는 아니다.
     * Phase 후속 작업에서 user BC에 {@code GetUserProfilesUseCase} 추가 예정.</p>
     *
     * @return userId → profile 매핑
     */
    Map<Long, UserProfile> getProfiles(List<Long> userIds);
}
