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

    /**
     * 복수 유저 공개 프로필을 한 번에 조회한다.
     *
     * <p>구현은 user BC 의 배치 유스케이스를 경유해 {@code user IN (:ids)} 단일 쿼리로 해결한다.
     * 탈퇴/삭제된 userId 는 placeholder(nickname·profileImageUrl null) 로 결과에 포함된다.</p>
     *
     * @return userId → profile 매핑 (모든 요청 ID 가 key 로 존재)
     */
    Map<Long, UserProfile> getProfiles(List<Long> userIds);
}
