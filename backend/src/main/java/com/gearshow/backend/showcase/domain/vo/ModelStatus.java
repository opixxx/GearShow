package com.gearshow.backend.showcase.domain.vo;

/**
 * 3D 모델 생성 상태.
 *
 * <p>상태 전이 규칙:</p>
 * <ul>
 *   <li>REQUESTED → GENERATING → COMPLETED</li>
 *   <li>GENERATING → FAILED</li>
 *   <li>FAILED → REQUESTED (재요청)</li>
 * </ul>
 */
public enum ModelStatus {

    /** 생성 요청됨 */
    REQUESTED,

    /** 생성 진행 중 */
    GENERATING,

    /** 생성 완료 */
    COMPLETED,

    /** 생성 실패 */
    FAILED
}
