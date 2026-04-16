package com.gearshow.backend.chat.application.port.in;

/**
 * 메시지 soft delete 유스케이스 (api-spec §8-8).
 *
 * <p>본인이 보낸 TEXT/IMAGE 메시지만 삭제 가능. 시스템 메시지는 삭제 불가.</p>
 */
public interface DeleteChatMessageUseCase {

    void delete(Long chatRoomId, Long chatMessageId, Long requesterId);
}
