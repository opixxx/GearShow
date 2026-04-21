package com.gearshow.backend.user.application.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetUserProfilesUseCase;
import com.gearshow.backend.user.application.port.out.UserPort;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 공개 프로필 배치 조회 유스케이스 구현체.
 *
 * <p>단일 {@code SELECT ... WHERE id IN (:ids)} 쿼리로 조회해 N+1 을 제거한다.</p>
 */
@Service
@RequiredArgsConstructor
public class GetUserProfilesService implements GetUserProfilesUseCase {

    private final UserPort userPort;

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileResult> getProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userPort.findAllByIds(userIds).stream()
                .map(UserProfileResult::from)
                .toList();
    }
}
