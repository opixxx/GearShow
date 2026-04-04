package com.gearshow.backend.user.adapter.in.web.dto;

/**
 * 닉네임 중복 확인 응답 DTO.
 *
 * @param nickname  확인한 닉네임
 * @param available 사용 가능 여부
 */
public record CheckNicknameResponse(
        String nickname,
        boolean available
) {
}
