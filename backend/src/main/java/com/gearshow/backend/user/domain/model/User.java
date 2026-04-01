package com.gearshow.backend.user.domain.model;

import com.gearshow.backend.user.domain.exception.InvalidUserStatusTransitionException;
import com.gearshow.backend.user.domain.exception.InvalidUserException;
import com.gearshow.backend.user.domain.vo.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 도메인 엔티티.
 *
 * <p>소셜 로그인을 통해 가입하며, 닉네임은 필수이고 중복될 수 없다.</p>
 */
@Getter
public class User {

    private final Long id;
    private final String nickname;
    private final String profileImageUrl;
    private final String phoneNumber;
    private final boolean phoneVerified;
    private final UserStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Builder
    private User(Long id, String nickname, String profileImageUrl,
                 String phoneNumber, boolean phoneVerified, UserStatus status,
                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.phoneNumber = phoneNumber;
        this.phoneVerified = phoneVerified;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 사용자를 생성한다.
     * 최초 상태는 ACTIVE이며, 휴대폰 인증은 미완료 상태이다.
     *
     * @param nickname 닉네임 (필수, 빈 값 불가)
     * @return 생성된 사용자
     */
    public static User create(String nickname) {
        validateNickname(nickname);

        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .nickname(nickname)
                .phoneVerified(false)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 사용자를 정지 상태로 전환한다.
     * ACTIVE 상태에서만 가능하다.
     *
     * @return 정지된 사용자
     */
    public User suspend() {
        validateStatusTransition(UserStatus.SUSPENDED);
        return User.builder()
                .id(this.id)
                .nickname(this.nickname)
                .profileImageUrl(this.profileImageUrl)
                .phoneNumber(this.phoneNumber)
                .phoneVerified(this.phoneVerified)
                .status(UserStatus.SUSPENDED)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 정지 상태를 해제하고 활성 상태로 복원한다.
     * SUSPENDED 상태에서만 가능하다.
     *
     * @return 활성화된 사용자
     */
    public User activate() {
        validateStatusTransition(UserStatus.ACTIVE);
        return User.builder()
                .id(this.id)
                .nickname(this.nickname)
                .profileImageUrl(this.profileImageUrl)
                .phoneNumber(this.phoneNumber)
                .phoneVerified(this.phoneVerified)
                .status(UserStatus.ACTIVE)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 사용자를 탈퇴 처리한다.
     * ACTIVE 상태에서만 가능하다.
     *
     * @return 탈퇴된 사용자
     */
    public User withdraw() {
        validateStatusTransition(UserStatus.WITHDRAWN);
        return User.builder()
                .id(this.id)
                .nickname(this.nickname)
                .profileImageUrl(this.profileImageUrl)
                .phoneNumber(this.phoneNumber)
                .phoneVerified(this.phoneVerified)
                .status(UserStatus.WITHDRAWN)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void validateStatusTransition(UserStatus target) {
        boolean valid = switch (this.status) {
            case ACTIVE -> target == UserStatus.SUSPENDED || target == UserStatus.WITHDRAWN;
            case SUSPENDED -> target == UserStatus.ACTIVE;
            case WITHDRAWN -> false;
        };

        if (!valid) {
            throw new InvalidUserStatusTransitionException();
        }
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new InvalidUserException();
        }
    }
}
