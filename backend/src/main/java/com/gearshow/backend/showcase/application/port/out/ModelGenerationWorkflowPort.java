package com.gearshow.backend.showcase.application.port.out;

/**
 * 3D 모델 생성 워크플로우(프로세스 테이블) 저장 포트.
 *
 * <p>도메인/프로세스 테이블 분리(ADR-010) 에 따라, 파이프라인 생명주기를 담당하는
 * {@code model_generation_workflow} 테이블에 대한 application 계층의 접근점이다.
 * 도메인 모델 승격은 Phase 1-D 에서 처리되며, 이번 Phase 는 JPA 엔티티를 어댑터에
 * 감추고 "생성 요청 기록" 만 노출한다.</p>
 */
public interface ModelGenerationWorkflowPort {

    /**
     * 지정 쇼케이스에 대한 신규 워크플로우를 {@code REQUESTED} 상태로 기록한다.
     *
     * @param showcaseId     쇼케이스 ID
     * @param idempotencyKey API {@code Idempotency-Key} 헤더 값 (ADR-011 ①,
     *                       {@code uk_mgw_idempotency_key} UNIQUE 식별자로 사용)
     * @param attemptNo      재시도 순번. 신규 생성은 {@code 1}, 재시도는 {@link #nextAttemptNo(Long)} 의 반환값
     * @return 영속화된 워크플로우 ID
     */
    long saveRequested(Long showcaseId, String idempotencyKey, int attemptNo);

    /**
     * 재시도를 위해 다음 {@code attempt_no} 를 계산한다.
     *
     * <p>해당 {@code showcaseId} 에 대한 가장 큰 {@code attempt_no} + 1 을 반환한다.
     * 이력이 없으면 {@code 1}. 조회는 {@code idx_mgw_showcase_attempt} 인덱스를 사용.</p>
     */
    int nextAttemptNo(Long showcaseId);
}
