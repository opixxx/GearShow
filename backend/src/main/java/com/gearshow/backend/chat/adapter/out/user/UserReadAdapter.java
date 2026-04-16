package com.gearshow.backend.chat.adapter.out.user;

import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.chat.application.port.out.UserReadPort;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetUserProfileUseCase;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chat → user 읽기 어댑터.
 *
 * <p>user BC의 공개 유스케이스({@link GetUserProfileUseCase})를 감싼다.
 * 탈퇴/삭제된 유저는 nickname·profileImageUrl이 null인 placeholder {@link UserProfile}로
 * 결과 맵에 포함된다 (호출측이 "(알 수 없음)" 문구로 렌더링할 수 있게).</p>
 */
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

    private final GetUserProfileUseCase getUserProfileUseCase;

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
        Map<Long, UserProfile> result = new HashMap<>();
        for (Long id : userIds) {
            try {
                result.put(id, toProfile(getUserProfileUseCase.getUserProfile(id)));
            } catch (NotFoundUserException e) {
                result.put(id, new UserProfile(id, null, null));
            }
        }
        return result;
    }

    private UserProfile toProfile(UserProfileResult r) {
        return new UserProfile(r.userId(), r.nickname(), r.profileImageUrl());
    }
}
