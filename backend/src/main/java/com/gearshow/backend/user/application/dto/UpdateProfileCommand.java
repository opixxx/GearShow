package com.gearshow.backend.user.application.dto;

/**
 * 프로필 수정 커맨드.
 *
 * @param nickname         변경할 닉네임 (null이면 변경하지 않음)
 * @param imageContent     프로필 이미지 바이트 배열 (null이면 이미지 변경하지 않음)
 * @param imageContentType 이미지 Content-Type (예: "image/jpeg")
 * @param imageFilename    원본 파일명 (확장자 추출용)
 */
public record UpdateProfileCommand(
        String nickname,
        byte[] imageContent,
        String imageContentType,
        String imageFilename
) {

    /**
     * 이미지 변경 요청이 포함되어 있는지 확인한다.
     */
    public boolean hasImage() {
        return imageContent != null && imageContent.length > 0;
    }
}
