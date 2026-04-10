package com.gearshow.backend.showcase.domain.vo;

/**
 * 3D 모델 생성 상태.
 *
 * <p>상태 전이 규칙:</p>
 * <ul>
 *   <li>REQUESTED → GENERATING (Worker 가 Tripo task 생성 성공)</li>
 *   <li>REQUESTED → FAILED (Tripo startGeneration 일반 실패)</li>
 *   <li>REQUESTED → UNAVAILABLE (Tripo Circuit Breaker OPEN — 서비스 장애)</li>
 *   <li>GENERATING → COMPLETED (폴링 스케줄러가 Tripo success 확인 후 결과 저장)</li>
 *   <li>GENERATING → FAILED (Tripo 실패 / 타임아웃)</li>
 *   <li>FAILED → REQUESTED (사용자 재요청)</li>
 *   <li>UNAVAILABLE → REQUESTED (사용자가 복구 후 수동 재요청)</li>
 * </ul>
 */
public enum ModelStatus {

    /** 생성 요청됨 */
    REQUESTED,

    /** 생성 진행 중 (Tripo task 생성 성공, 폴링 대기) */
    GENERATING,

    /** 생성 완료 */
    COMPLETED,

    /** 생성 실패 */
    FAILED,

    /**
     * 3D 생성 서비스 일시 이용 불가.
     * Tripo Circuit Breaker 가 OPEN 상태일 때 사용자에게 노출되는 상태.
     * 사용자는 서비스 복구 후 수동으로 재시도할 수 있다.
     */
    UNAVAILABLE
}
