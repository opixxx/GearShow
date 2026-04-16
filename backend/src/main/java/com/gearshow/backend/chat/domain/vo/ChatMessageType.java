package com.gearshow.backend.chat.domain.vo;

/**
 * 채팅 메시지 타입.
 *
 * <p>Phase 1 REST MVP는 {@link #TEXT}만 송신을 허용한다.
 * IMAGE는 Phase 4, SYSTEM_* 시리즈는 Phase 5~6 거래 티켓/거래 플로우에서 자동 삽입된다.</p>
 */
public enum ChatMessageType {

    /** 사용자가 입력한 텍스트 메시지 */
    TEXT,

    /** 이미지 첨부 메시지 (Phase 4 이후) */
    IMAGE,

    /** 시스템: 거래 티켓 발급 */
    SYSTEM_TICKET_ISSUED,

    /** 시스템: 거래 시작 */
    SYSTEM_TRANSACTION_STARTED,

    /** 시스템: 결제 완료 */
    SYSTEM_PAYMENT_COMPLETED,

    /** 시스템: 거래 완료 */
    SYSTEM_TRANSACTION_COMPLETED,

    /** 시스템: 거래 취소 */
    SYSTEM_TRANSACTION_CANCELLED;

    /**
     * 사용자 발신 가능한 타입인지 여부.
     * SYSTEM_* 계열은 서버가 자동 삽입하므로 클라이언트 송신 금지.
     */
    public boolean isUserSendable() {
        return this == TEXT || this == IMAGE;
    }

    /**
     * 시스템 메시지 여부.
     * 시스템 메시지는 sender가 없고 soft delete가 불가하다.
     */
    public boolean isSystem() {
        return !isUserSendable();
    }
}
