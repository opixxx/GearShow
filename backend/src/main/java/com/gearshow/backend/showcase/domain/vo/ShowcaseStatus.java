package com.gearshow.backend.showcase.domain.vo;

/**
 * 쇼케이스 상태.
 *
 * <p>상태 전이 규칙:</p>
 * <ul>
 *   <li>ACTIVE → HIDDEN (소유자 비공개 전환)</li>
 *   <li>HIDDEN → ACTIVE (소유자 공개 전환)</li>
 *   <li>ACTIVE → DELETED (소유자 삭제)</li>
 *   <li>HIDDEN → DELETED (소유자 삭제)</li>
 *   <li>ACTIVE → SOLD (거래 완료)</li>
 * </ul>
 */
public enum ShowcaseStatus {

    /** 공개 상태 */
    ACTIVE,

    /** 비공개 */
    HIDDEN,

    /** 판매 완료 */
    SOLD,

    /** 삭제됨 (소프트 삭제) */
    DELETED
}
