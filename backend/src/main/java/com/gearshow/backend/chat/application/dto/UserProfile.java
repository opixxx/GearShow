package com.gearshow.backend.chat.application.dto;

/**
 * 유저 프로필 요약 정보 (UserReadPort 결과).
 *
 * <p>탈퇴/삭제된 유저의 placeholder는 {@code nickname}, {@code profileImageUrl} 모두 null로 채워진다.</p>
 *
 * @param userId          유저 ID
 * @param nickname        닉네임 (탈퇴/삭제 시 null)
 * @param profileImageUrl 프로필 이미지 URL (미설정 또는 탈퇴/삭제 시 null)
 */
public record UserProfile(Long userId, String nickname, String profileImageUrl) {
}
