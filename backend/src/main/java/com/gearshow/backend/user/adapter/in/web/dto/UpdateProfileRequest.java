package com.gearshow.backend.user.adapter.in.web.dto;

import com.gearshow.backend.user.application.dto.UpdateProfileCommand;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 프로필 수정 요청 DTO.
 *
 * @param nickname        변경할 닉네임
 * @param profileImageUrl 변경할 프로필 이미지 URL
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
        String nickname,

        @URL(message = "유효한 URL 형식이어야 합니다")
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
