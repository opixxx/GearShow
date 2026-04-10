package com.gearshow.backend.user.application.service;

import com.gearshow.backend.support.TestOAuthConfig;
import com.gearshow.backend.user.adapter.out.persistence.UserJpaRepository;
import com.gearshow.backend.user.application.dto.*;
import com.gearshow.backend.user.application.exception.DuplicateNicknameException;
import com.gearshow.backend.user.application.port.in.*;
import com.gearshow.backend.user.domain.exception.NotFoundUserException;
import com.gearshow.backend.user.domain.vo.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
@Transactional
class UserProfileServiceIntegrationTest {

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private GetMyProfileUseCase getMyProfileUseCase;

    @Autowired
    private GetUserProfileUseCase getUserProfileUseCase;

    @Autowired
    private UpdateProfileUseCase updateProfileUseCase;

    @Autowired
    private WithdrawUseCase withdrawUseCase;

    @Autowired
    private UserJpaRepository userJpaRepository;

    /**
     * 테스트용 사용자를 생성하고 userId를 반환한다.
     */
    private Long createUser(String authCode) {
        loginUseCase.login(new LoginCommand("kakao", authCode, null));
        return userJpaRepository.findAll().stream()
                .reduce((first, second) -> second)
                .orElseThrow()
                .getId();
    }

    @Nested
    @DisplayName("내 프로필 조회")
    class GetMyProfile {

        @Test
        @DisplayName("인증된 사용자의 프로필을 조회한다")
        void getMyProfile_returnsProfile() {
            // Given
            Long userId = createUser("valid-code-myprofile");

            // When
            MyProfileResult result = getMyProfileUseCase.getMyProfile(userId);

            // Then
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.nickname()).isNotBlank();
            assertThat(result.userStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("존재하지 않는 사용자를 조회하면 예외가 발생한다")
        void getMyProfile_notFound_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> getMyProfileUseCase.getMyProfile(999L))
                    .isInstanceOf(NotFoundUserException.class);
        }
    }

    @Nested
    @DisplayName("다른 사용자 프로필 조회")
    class GetUserProfile {

        @Test
        @DisplayName("다른 사용자의 공개 프로필을 조회한다")
        void getUserProfile_returnsPublicProfile() {
            // Given
            Long userId = createUser("valid-code-public");

            // When
            UserProfileResult result = getUserProfileUseCase.getUserProfile(userId);

            // Then
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.nickname()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임을 수정한다")
        void updateProfile_changesNickname() {
            // Given
            Long userId = createUser("valid-code-update1");
            UpdateProfileCommand command = new UpdateProfileCommand("새닉네임", null, null, null);

            // When
            UpdateProfileResult result = updateProfileUseCase.updateProfile(userId, command);

            // Then
            assertThat(result.nickname()).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("중복 닉네임으로 수정하면 예외가 발생한다")
        void updateProfile_duplicateNickname_throwsException() {
            // Given
            Long userId1 = createUser("valid-code-dup1");
            Long userId2 = createUser("valid-code-dup2");

            MyProfileResult firstUser = getMyProfileUseCase.getMyProfile(userId1);
            UpdateProfileCommand command = new UpdateProfileCommand(firstUser.nickname(), null, null, null);

            // When & Then
            assertThatThrownBy(() -> updateProfileUseCase.updateProfile(userId2, command))
                    .isInstanceOf(DuplicateNicknameException.class);
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("사용자를 탈퇴 처리한다")
        void withdraw_changesStatusToWithdrawn() {
            // Given
            Long userId = createUser("valid-code-withdraw1");

            // When
            withdrawUseCase.withdraw(userId);

            // Then
            MyProfileResult result = getMyProfileUseCase.getMyProfile(userId);
            assertThat(result.userStatus()).isEqualTo(UserStatus.WITHDRAWN);
        }
    }
}
