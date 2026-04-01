package com.gearshow.backend.user.domain.vo;

/**
 * 사용자 상태를 나타내는 값 객체.
 *
 * <p>상태 전이 규칙:</p>
 * <ul>
 *   <li>ACTIVE → SUSPENDED (관리자 정지)</li>
 *   <li>SUSPENDED → ACTIVE (관리자 해제)</li>
 *   <li>ACTIVE → WITHDRAWN (본인 탈퇴)</li>
 * </ul>
 */
public enum UserStatus {

    /** 정상 활동 가능 */
    ACTIVE,

    /** 관리자에 의해 정지됨 */
    SUSPENDED,

    /** 탈퇴 완료 */
    WITHDRAWN
}
