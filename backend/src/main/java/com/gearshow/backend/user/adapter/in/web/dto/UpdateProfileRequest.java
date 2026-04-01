package com.gearshow.backend.user.adapter.in.web.dto;

import com.gearshow.backend.user.application.dto.UpdateProfileCommand;

/**
 * 프로필 수정 요청 DTO.
 *
 * @param nickname        변경할 닉네임
 * @param profileImageUrl 변경할 프로필 이미지 URL
 */
public record UpdateProfileRequest(
        String nickname,
        String profileImageUrl
) {

    /**
     * 요청을 커맨드로 변환한다.
     *
     * @return 프로필 수정 커맨드
     */
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(nickname, profileImageUrl);
    }
}
