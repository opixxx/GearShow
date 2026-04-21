package com.gearshow.backend.chat.adapter.out.user;

import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetUserProfileUseCase;
import com.gearshow.backend.user.application.port.in.GetUserProfilesUseCase;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserReadAdapterTest {

    @InjectMocks
    private UserReadAdapter adapter;

    @Mock private GetUserProfileUseCase getUserProfileUseCase;
    @Mock private GetUserProfilesUseCase getUserProfilesUseCase;

    @Test
    @DisplayName("정상 조회 시 UserProfile로 변환")
    void getProfile_success() {
        given(getUserProfileUseCase.getUserProfile(1L))
                .willReturn(new UserProfileResult(1L, "nick", "img.jpg"));

        UserProfile profile = adapter.getProfile(1L);

        assertThat(profile.nickname()).isEqualTo("nick");
        assertThat(profile.profileImageUrl()).isEqualTo("img.jpg");
    }

    @Test
    @DisplayName("NotFoundUserException 발생 시 nickname/profileImageUrl이 null인 placeholder 반환")
    void getProfile_notFound_returnsPlaceholder() {
        given(getUserProfileUseCase.getUserProfile(99L))
                .willThrow(new NotFoundUserException());

        UserProfile profile = adapter.getProfile(99L);

        assertThat(profile.userId()).isEqualTo(99L);
        assertThat(profile.nickname()).isNull();
        assertThat(profile.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("getProfiles는 배치 UseCase 1회 호출로 매핑하며 없는 ID는 placeholder로 채운다")
    void getProfiles_mixedNormalAndNotFound() {
        // given: batch UseCase 가 존재하는 1L 만 돌려주고 2L 은 누락
        given(getUserProfilesUseCase.getProfiles(List.of(1L, 2L)))
                .willReturn(List.of(new UserProfileResult(1L, "a", "u1")));

        // when
        Map<Long, UserProfile> result = adapter.getProfiles(List.of(1L, 2L));

        // then: 1L 은 정상, 2L 은 placeholder(nickname null) 로 채워져 모든 요청 ID가 맵에 존재
        assertThat(result.get(1L).nickname()).isEqualTo("a");
        assertThat(result.get(2L).nickname()).isNull();
        assertThat(result).hasSize(2);
    }
}
