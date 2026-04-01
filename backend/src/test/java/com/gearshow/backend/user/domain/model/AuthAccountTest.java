package com.gearshow.backend.user.domain.model;

import com.gearshow.backend.user.domain.exception.InvalidAuthAccountException;
import com.gearshow.backend.user.domain.vo.AuthStatus;
import com.gearshow.backend.user.domain.vo.ProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAccountTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("유효한 정보로 인증 계정을 생성하면 LINKED 상태이다")
        void create_withValidInfo_returnsLinkedAccount() {
            // Given & When
            AuthAccount account = AuthAccount.create(1L, ProviderType.KAKAO, "kakao-user-123");

            // Then
            assertThat(account.getUserId()).isEqualTo(1L);
            assertThat(account.getProviderType()).isEqualTo(ProviderType.KAKAO);
            assertThat(account.getProviderUserKey()).isEqualTo("kakao-user-123");
            assertThat(account.getAuthStatus()).isEqualTo(AuthStatus.LINKED);
            assertThat(account.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다")
        void create_withNullUserId_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> AuthAccount.create(null, ProviderType.KAKAO, "key"))
                    .isInstanceOf(InvalidAuthAccountException.class);
        }

        @Test
        @DisplayName("providerUserKey가 빈 문자열이면 예외가 발생한다")
        void create_withBlankProviderUserKey_throwsException() {
            // Given & When & Then
            assertThatThrownBy(() -> AuthAccount.create(1L, ProviderType.KAKAO, "  "))
                    .isInstanceOf(InvalidAuthAccountException.class);
        }
    }

    @Nested
    @DisplayName("상태 변경")
    class StateChange {

        @Test
        @DisplayName("로그인 시각을 갱신하면 lastLoginAt이 변경된다")
        void updateLastLogin_updatesTimestamp() {
            // Given
            AuthAccount account = AuthAccount.create(1L, ProviderType.KAKAO, "kakao-user-123");

            // When
            AuthAccount updated = account.updateLastLogin();

            // Then
            assertThat(updated.getLastLoginAt()).isNotNull();
            assertThat(updated.getProviderUserKey()).isEqualTo("kakao-user-123");
        }

        @Test
        @DisplayName("연동을 해제하면 UNLINKED 상태가 된다")
        void unlink_changesStatusToUnlinked() {
            // Given
            AuthAccount account = AuthAccount.create(1L, ProviderType.KAKAO, "kakao-user-123");

            // When
            AuthAccount unlinked = account.unlink();

            // Then
            assertThat(unlinked.getAuthStatus()).isEqualTo(AuthStatus.UNLINKED);
        }
    }
}
