package com.gearshow.backend.showcase.adapter.out.user;

import com.gearshow.backend.showcase.application.dto.UserProfile;
import com.gearshow.backend.showcase.application.port.out.UserReadPort;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetUserProfileUseCase;
import com.gearshow.backend.user.application.port.in.GetUserProfilesUseCase;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * showcase → user 읽기 어댑터.
 *
 * <p>user BC 의 공개 유스케이스({@link GetUserProfileUseCase}, {@link GetUserProfilesUseCase}) 를 감싼다.
 * 탈퇴/삭제된 유저는 nickname·profileImageUrl 이 {@code null} 인 placeholder {@link UserProfile} 로
 * 결과 맵에 포함된다 (호출측이 "(알 수 없음)" 등으로 렌더링).</p>
 *
 * <p>배치 조회({@link #getProfiles}) 는 {@link GetUserProfilesUseCase} 를 통해
 * 단일 {@code IN} 쿼리로 해결해 N+1 을 제거한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ShowcaseUserReadAdapter implements UserReadPort {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final GetUserProfilesUseCase getUserProfilesUseCase;

    @Override
    public UserProfile getProfile(Long userId) {
        try {
            return toProfile(getUserProfileUseCase.getUserProfile(userId));
        } catch (NotFoundUserException e) {
            return new UserProfile(userId, null, null);
        }
    }

    @Override
    public Map<Long, UserProfile> getProfiles(List<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds 는 null 일 수 없다");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserProfileResult> profiles = getUserProfilesUseCase.getProfiles(userIds);

        Map<Long, UserProfile> result = new HashMap<>();
        for (UserProfileResult profile : profiles) {
            // user BC 결과 신뢰 가드 — userId 가 비정상으로 null 인 경우 무시 (계약상 placeholder 로 채워질 것)
            if (profile.userId() != null) {
                result.put(profile.userId(), toProfile(profile));
            }
        }
        // 탈퇴/삭제된 userId 는 placeholder 로 채워 호출측 계약 유지
        for (Long id : userIds) {
            result.putIfAbsent(id, new UserProfile(id, null, null));
        }
        return result;
    }

    private UserProfile toProfile(UserProfileResult r) {
        return new UserProfile(r.userId(), r.nickname(), r.profileImageUrl());
    }
}
