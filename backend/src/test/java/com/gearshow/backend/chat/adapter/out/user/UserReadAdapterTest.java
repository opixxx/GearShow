package com.gearshow.backend.chat.adapter.out.user;

import com.gearshow.backend.chat.application.dto.UserProfile;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.GetUserProfileUseCase;
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
    @DisplayName("getProfiles는 ID별로 조회하며 NotFound는 placeholder로 채운다")
    void getProfiles_mixedNormalAndNotFound() {
        given(getUserProfileUseCase.getUserProfile(1L))
                .willReturn(new UserProfileResult(1L, "a", "u1"));
        given(getUserProfileUseCase.getUserProfile(2L))
                .willThrow(new NotFoundUserException());

        Map<Long, UserProfile> result = adapter.getProfiles(List.of(1L, 2L));

        assertThat(result.get(1L).nickname()).isEqualTo("a");
        assertThat(result.get(2L).nickname()).isNull();
        assertThat(result).hasSize(2);
    }
}
