package com.gearshow.backend.chat.adapter.out.user;

import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.chat.application.port.out.UserReadPort;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetUserProfilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chat → user 읽기 어댑터.
 *
 * <p>user BC 의 공개 유스케이스({@link GetUserProfilesUseCase}) 를 감싸 단일 {@code IN} 쿼리로
 * 배치 조회한다. 탈퇴/삭제된 userId 는 nickname·profileImageUrl 이 {@code null} 인 placeholder
 * {@link UserProfile} 로 결과 맵에 포함되며, 호출측이 "(알 수 없음)" 등으로 렌더링한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatUserReadAdapter implements UserReadPort {

    private final GetUserProfilesUseCase getUserProfilesUseCase;

    @Override
    public Map<Long, UserProfile> getProfiles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserProfileResult> profiles = getUserProfilesUseCase.getProfiles(userIds);

        Map<Long, UserProfile> result = new HashMap<>();
        for (UserProfileResult profile : profiles) {
            result.put(profile.userId(), toProfile(profile));
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
