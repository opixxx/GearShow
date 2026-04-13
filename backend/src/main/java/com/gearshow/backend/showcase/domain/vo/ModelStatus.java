package com.gearshow.backend.showcase.domain.vo;

/**
 * 3D 모델 생성 상태.
 *
 * <p>상태 전이 규칙:</p>
 * <ul>
 *   <li>REQUESTED → PREPARING (Worker 가 메시지를 잡고 Tripo 호출을 준비)</li>
 *   <li>PREPARING → GENERATING (Tripo task 생성 성공, taskId 와 함께 전환)</li>
 *   <li>PREPARING → FAILED (Tripo Non-retryable 에러: 크레딧 부족, 이미지 거부 등)</li>
 *   <li>PREPARING → UNAVAILABLE (Tripo Circuit Breaker OPEN — 서비스 장애)</li>
 *   <li>PREPARING → REQUESTED (크래시 후 Recovery 자동 재시도, retryCount 적용)</li>
 *   <li>REQUESTED → FAILED (Tripo startGeneration 일반 실패 — 레거시 경로)</li>
 *   <li>REQUESTED → UNAVAILABLE (레거시 경로)</li>
 *   <li>GENERATING → COMPLETED (폴링 스케줄러가 Tripo success 확인 후 결과 저장)</li>
 *   <li>GENERATING → FAILED (Tripo 실패 / 타임아웃)</li>
 *   <li>FAILED → REQUESTED (사용자 재요청)</li>
 *   <li>UNAVAILABLE → REQUESTED (사용자가 복구 후 수동 재요청)</li>
 * </ul>
 */
public enum ModelStatus {

    /** 생성 요청됨 — Outbox 에 메시지가 쌓여있고 Worker 가 아직 잡지 않은 상태 */
    REQUESTED,

    /**
     * 생성 준비 중 — Worker 가 메시지를 잡고 Tripo 호출을 준비하는 상태.
     *
     * <p>이 상태의 핵심 목적은 "외부 API 호출 전에 의도를 기록" 하는 것이다.
     * PREPARING 상태에서는 generationTaskId 가 반드시 NULL 이며,
     * 이는 Tripo 과금이 아직 발생하지 않았음을 보장한다.
     * 따라서 이 상태에서의 크래시 복구는 안전하게 재시도할 수 있다.</p>
     */
    PREPARING,

    /** 생성 진행 중 — Tripo task 생성 성공, generationTaskId 가 반드시 존재. 폴링 대기 */
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
