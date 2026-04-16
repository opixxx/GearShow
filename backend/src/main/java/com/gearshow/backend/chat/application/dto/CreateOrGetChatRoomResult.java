package com.gearshow.backend.chat.application.dto;

/**
 * 채팅방 생성-또는-조회 결과 DTO (api-spec §8-3).
 *
 * @param chatRoomId 채팅방 ID
 * @param created    true면 신규 생성(201), false면 기존 반환(200)
 */
public record CreateOrGetChatRoomResult(Long chatRoomId, boolean created) {
}
