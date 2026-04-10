package com.gearshow.backend.user.application.service;

import com.gearshow.backend.support.TestInfraConfig;
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
@Import({TestOAuthConfig.class, TestInfraConfig.class})
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

        @Test
        @DisplayName("프로필 이미지를 업로드하면 CDN URL이 저장된다")
        void updateProfile_uploadsImage_savesCdnUrl() {
            // Given
            Long userId = createUser("valid-code-img1");
            byte[] imageBytes = new byte[]{1, 2, 3, 4};
            UpdateProfileCommand command = new UpdateProfileCommand(
                    null, imageBytes, "image/jpeg", "photo.jpg");

            // When
            UpdateProfileResult result = updateProfileUseCase.updateProfile(userId, command);

            // Then
            assertThat(result.profileImageUrl())
                    .startsWith("https://test-cdn.gearshow.com/profiles/");
        }

        @Test
        @DisplayName("기존 이미지가 있는 상태에서 새 이미지를 업로드하면 기존 이미지가 삭제된다")
        void updateProfile_replacesImage_deletesOldImage() {
            // Given
            Long userId = createUser("valid-code-img2");
            // 첫 업로드
            updateProfileUseCase.updateProfile(userId, new UpdateProfileCommand(
                    null, new byte[]{1}, "image/jpeg", "first.jpg"));
            // 두 번째 업로드
            UpdateProfileResult result = updateProfileUseCase.updateProfile(userId, new UpdateProfileCommand(
                    null, new byte[]{2}, "image/jpeg", "second.jpg"));

            // Then: 새 이미지 URL이 반환되고 첫 번째와 다르다
            assertThat(result.profileImageUrl())
                    .startsWith("https://test-cdn.gearshow.com/profiles/")
                    .doesNotContain("first.jpg");
        }

        @Test
        @DisplayName("이미지 없이 닉네임만 수정하면 기존 이미지가 유지된다")
        void updateProfile_nicknameOnly_keepsImage() {
            // Given
            Long userId = createUser("valid-code-img3");
            // 이미지 업로드
            UpdateProfileResult firstResult = updateProfileUseCase.updateProfile(userId, new UpdateProfileCommand(
                    null, new byte[]{1}, "image/jpeg", "photo.jpg"));
            String oldImageUrl = firstResult.profileImageUrl();

            // When: 닉네임만 변경
            UpdateProfileResult result = updateProfileUseCase.updateProfile(userId, new UpdateProfileCommand(
                    "닉네임바뀜", null, null, null));

            // Then
            assertThat(result.nickname()).isEqualTo("닉네임바뀜");
            assertThat(result.profileImageUrl()).isEqualTo(oldImageUrl);
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
