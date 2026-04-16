package com.gearshow.backend.chat.application.port.in;

import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.dto.SendChatMessageResult;

/**
 * 메시지 송신 유스케이스 (api-spec §8-5).
 *
 * <p>Phase 1 REST MVP는 TEXT만 허용한다.
 * 동일 {@code clientMessageId} 재시도는 409와 함께 기존 메시지 정보로 응답한다.</p>
 */
public interface SendChatMessageUseCase {

    SendChatMessageResult send(SendChatMessageCommand command);
}
