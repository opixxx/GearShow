package com.gearshow.backend.chat.domain.vo;

/**
 * 채팅 메시지 상태.
 *
 * <p>DELETED는 본인 메시지 soft delete 결과이며, 목록 조회 시
 * "삭제된 메시지입니다" 플레이스홀더로 노출된다.</p>
 */
public enum ChatMessageStatus {

    /** 정상 노출 */
    ACTIVE,

    /** 본인이 소프트 삭제한 메시지 */
    DELETED
}
