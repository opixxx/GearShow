package com.gearshow.backend.user.domain.policy;

import com.gearshow.backend.user.domain.exception.ProfileImageTooLargeException;
import com.gearshow.backend.user.domain.exception.UnsupportedProfileImageTypeException;

import java.util.Set;

/**
 * 프로필 이미지 검증 정책.
 * 크기 / Content-Type 화이트리스트 검증을 담당한다.
 */
public final class ProfileImagePolicy {

    /** 프로필 이미지 최대 크기 (5MB). */
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    /** 허용 Content-Type 화이트리스트. */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private ProfileImagePolicy() {
        // 유틸리티 클래스이므로 인스턴스화 금지
    }

    /**
     * 프로필 이미지 바이트 배열과 Content-Type을 검증한다.
     *
     * @param content     이미지 바이트 배열
     * @param contentType 이미지 Content-Type
     * @throws ProfileImageTooLargeException        크기가 5MB를 초과하는 경우
     * @throws UnsupportedProfileImageTypeException 허용되지 않는 Content-Type인 경우
     */
    public static void validate(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            return;
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new ProfileImageTooLargeException();
        }
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new UnsupportedProfileImageTypeException();
        }
    }
}
