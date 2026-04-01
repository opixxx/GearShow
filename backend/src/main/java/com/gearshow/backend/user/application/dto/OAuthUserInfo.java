package com.gearshow.backend.user.application.dto;

/**
 * OAuth 인증 후 받아오는 사용자 정보.
 *
 * @param providerUserKey 제공자 측 사용자 고유 키
 * @param nickname        닉네임 (제공자에 따라 null 가능)
 * @param profileImageUrl 프로필 이미지 URL (제공자에 따라 null 가능)
 */
public record OAuthUserInfo(
        String providerUserKey,
        String nickname,
        String profileImageUrl
) {
}
