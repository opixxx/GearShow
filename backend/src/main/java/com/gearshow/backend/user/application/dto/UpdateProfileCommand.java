package com.gearshow.backend.user.application.dto;

/**
 * 프로필 수정 커맨드.
 *
 * @param nickname        변경할 닉네임 (null이면 변경하지 않음)
 * @param profileImageUrl 변경할 프로필 이미지 URL (null이면 변경하지 않음)
 */
public record UpdateProfileCommand(
        String nickname,
        String profileImageUrl
) {
}
