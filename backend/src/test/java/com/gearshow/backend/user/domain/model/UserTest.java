package com.gearshow.backend.user.domain.model;

import com.gearshow.backend.user.domain.exception.InvalidUserException;
import com.gearshow.backend.user.domain.exception.InvalidUserStatusTransitionException;
import com.gearshow.backend.user.domain.vo.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 닉네임으로 사용자를 생성하면 ACTIVE 상태이다")
        void create_withValidNickname_returnsActiveUser() {
            // Given & When
            User user = User.create("테스트유저");

            // Then
            assertThat(user.getNickname()).isEqualTo("테스트유저");
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isPhoneVerified()).isFalse();
            assertThat(user.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("닉네임이 null이면 예외가 발생한다")
        void create_withNullNickname_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> User.create(null))
                    .isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("닉네임이 빈 문자열이면 예외가 발생한다")
        void create_withBlankNickname_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> User.create("  "))
                    .isInstanceOf(InvalidUserException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("ACTIVE 사용자를 정지하면 SUSPENDED 상태가 된다")
        void suspend_fromActive_returnsSuspended() {
            // Given
            User user = User.create("테스트유저");

            // When
            User suspended = user.suspend();

            // Then
            assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("SUSPENDED 사용자를 활성화하면 ACTIVE 상태가 된다")
        void activate_fromSuspended_returnsActive() {
            // Given
            User user = User.create("테스트유저").suspend();

            // When
            User activated = user.activate();

            // Then
            assertThat(activated.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 사용자를 탈퇴하면 WITHDRAWN 상태가 된다")
        void withdraw_fromActive_returnsWithdrawn() {
            // Given
            User user = User.create("테스트유저");

            // When
            User withdrawn = user.withdraw();

            // Then
            assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("WITHDRAWN 사용자를 활성화하면 예외가 발생한다")
        void activate_fromWithdrawn_throwsException() {
            // Given
            User user = User.create("테스트유저").withdraw();

            // When & Then
            assertThatThrownBy(user::activate)
                    .isInstanceOf(InvalidUserStatusTransitionException.class);
        }

        @Test
        @DisplayName("SUSPENDED 사용자를 탈퇴하면 예외가 발생한다")
        void withdraw_fromSuspended_throwsException() {
            // Given
            User user = User.create("테스트유저").suspend();

            // When & Then
            assertThatThrownBy(user::withdraw)
                    .isInstanceOf(InvalidUserStatusTransitionException.class);
        }
    }
}
