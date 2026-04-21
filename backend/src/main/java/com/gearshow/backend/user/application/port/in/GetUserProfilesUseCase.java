package com.gearshow.backend.user.application.port.in;

import com.gearshow.backend.user.application.dto.UserProfileResult;

import java.util.Collection;
import java.util.List;

/**
 * 여러 사용자 공개 프로필을 한 번에 조회하는 유스케이스.
 *
 * <p>N+1 을 피하기 위해 chat 등 외부 컨텍스트가 배치 조회에 사용한다.
 * 단일 조회가 필요하면 {@link GetUserProfileUseCase} 를 사용한다.</p>
 */
public interface GetUserProfilesUseCase {

    /**
     * 복수 사용자 공개 프로필을 조회한다. 존재하지 않는 ID 는 결과에서 누락된다.
     *
     * @param userIds 사용자 ID 컬렉션. 빈 컬렉션이면 빈 리스트 반환.
     * @return 조회된 프로필 목록 (순서 보장 없음)
     */
    List<UserProfileResult> getProfiles(Collection<Long> userIds);
}
