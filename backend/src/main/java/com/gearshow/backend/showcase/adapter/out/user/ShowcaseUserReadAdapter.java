package com.gearshow.backend.showcase.adapter.out.user;

import com.gearshow.backend.showcase.application.dto.UserProfile;
import com.gearshow.backend.showcase.application.port.out.UserReadPort;
import com.gearshow.backend.user.application.port.in.GetUserProfilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * showcase → user 읽기 어댑터.
 *
 * <p>{@link GetUserProfilesUseCase} 를 단일 {@code IN} 쿼리로 호출하여 N+1 을 제거한다.
 * 탈퇴/삭제된 userId 는 nickname·profileImageUrl 이 {@code null} 인 placeholder {@link UserProfile}
 * 로 결과 맵에 채워져 모든 요청 ID 가 키로 존재한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ShowcaseUserReadAdapter implements UserReadPort {

    private final GetUserProfilesUseCase getUserProfilesUseCase;

    @Override
    public Map<Long, UserProfile> getProfiles(List<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds 는 null 일 수 없다");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> profiles = getUserProfilesUseCase.getProfiles(userIds).stream()
                .filter(r -> r.userId() != null)
                .collect(Collectors.toMap(
                        r -> r.userId(),
                        r -> new UserProfile(r.userId(), r.nickname(), r.profileImageUrl())
                ));
        // 탈퇴/삭제된 userId 는 placeholder 로 채워 호출측 계약 유지
        userIds.forEach(id -> profiles.putIfAbsent(id, new UserProfile(id, null, null)));
        return profiles;
    }
}
