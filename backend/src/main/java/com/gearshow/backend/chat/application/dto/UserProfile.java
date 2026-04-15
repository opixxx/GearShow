package com.gearshow.backend.chat.application.dto;

/**
 * 유저 프로필 요약 정보 (UserReadPort 결과).
 *
 * @param userId          유저 ID
 * @param nickname        닉네임
 * @param profileImageUrl 프로필 이미지 URL (null 가능)
 */
public record UserProfile(Long userId, String nickname, String profileImageUrl) {
}
