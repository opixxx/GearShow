package com.gearshow.backend.user.domain.vo;

import com.gearshow.backend.user.domain.exception.UnsupportedProviderException;

/**
 * 소셜 로그인 인증 제공자 유형.
 */
public enum ProviderType {

    /** 카카오 */
    KAKAO,

    /** 구글 */
    GOOGLE,

    /** 애플 */
    APPLE;

    /**
     * 문자열 제공자명을 ProviderType으로 변환한다.
     *
     * @param provider 소셜 제공자명 (예: "kakao", "apple", "google")
     * @return 대응하는 ProviderType
     * @throws UnsupportedProviderException 지원하지 않는 제공자인 경우
     */
    public static ProviderType from(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new UnsupportedProviderException();
        }
        return switch (provider.toLowerCase()) {
            case "kakao" -> KAKAO;
            case "apple" -> APPLE;
            case "google" -> GOOGLE;
            default -> throw new UnsupportedProviderException();
        };
    }
}
