package com.gearshow.backend.chat.adapter.in.websocket;

import java.security.Principal;

/**
 * STOMP 세션에 바인딩되는 인증 주체.
 */
public record StompPrincipal(Long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
