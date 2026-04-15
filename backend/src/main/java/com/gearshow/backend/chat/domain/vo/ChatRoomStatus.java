package com.gearshow.backend.chat.domain.vo;

/**
 * 채팅방 상태.
 *
 * <p>쇼케이스가 SOLD/DELETED로 전이되면 관련 채팅방은 CLOSED로 전환된다.
 * CLOSED 상태에서는 메시지 송신이 불가하나 읽기·읽음 처리·본인 메시지 삭제는 허용된다.</p>
 */
public enum ChatRoomStatus {

    /** 활성 (메시지 송수신 가능) */
    ACTIVE,

    /** 종료 (읽기만 가능, 신규 메시지 송신 불가) */
    CLOSED
}
