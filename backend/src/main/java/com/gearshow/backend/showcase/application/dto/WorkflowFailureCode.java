package com.gearshow.backend.showcase.application.dto;

/**
 * 3D 모델 생성 워크플로우 실패 코드.
 *
 * <p>실패 분류는 점진적으로 확장된다 — TX1 경로(P1-D-α+β) 입력 검증 실패에서 시작해
 * Tripo 호출(P1-D-γ+δ) · Reconcile(P1-G) 에서 코드가 추가된다.</p>
 */
public enum WorkflowFailureCode {

    /** 소스 이미지 4장 요건 미충족 (DB 상태 불일치). */
    SOURCE_IMAGES_MISSING,

    /** 소스 이미지의 S3 객체가 존재하지 않음. 클라이언트 업로드 누락 또는 lifecycle 삭제. */
    S3_KEY_MISSING,

    /**
     * Tripo Non-Retryable 응답 (400/401/403 등). 즉시 FAILED.
     * {@code failure_message} 에 Tripo 측 원인을 저장한다.
     */
    TRIPO_NON_RETRYABLE,

    /**
     * Tripo {@code POST /task} 성공 후 {@code tripo_pending_task} 선저장이 실패한 경우.
     * task_id 가 유실될 위험이 있으며 Reconcile(P1-G) 이 중복 task 정리를 담당한다.
     */
    TRIPO_PENDING_SAVE_FAILED,

    /**
     * {@code @RetryableTopic} 재시도가 모두 소진되어 DLT 에 도달한 경우.
     * {@code @DltHandler} 가 이 값으로 마킹한다. 운영자 수동 확인 또는 Reconcile 이 후처리.
     */
    TRIPO_RETRY_EXHAUSTED,

    /**
     * TX2 ({@code PREPARING → GENERATING}) DB 작업이 예외로 실패한 경우. 정상 흐름에선
     * 조건부 UPDATE 의 affected=0 을 통해 조용히 처리되므로 이 코드는 드물다.
     * Reconcile 이 {@code tripo_pending_task} 를 활용해 복구한다.
     */
    TX2_DB_FAILED
}
