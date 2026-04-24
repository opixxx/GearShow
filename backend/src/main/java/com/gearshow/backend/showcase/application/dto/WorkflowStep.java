package com.gearshow.backend.showcase.application.dto;

/**
 * 3D 모델 생성 워크플로우의 현재 단계.
 *
 * <p>4-state 머신 (설계 문서 §5). GENERATING 내부 서브-단계(Tripo 처리 중 vs S3 미러링 중)는
 * 워크플로우의 {@code tripo_succeeded_at} 컬럼으로 구분한다.</p>
 *
 * <p><b>이전 위치</b>: 이 enum 은 원래 {@code ModelGenerationWorkflowJpaEntity.CurrentStep}
 * 중첩 enum 이었다. application 계층이 상태 값을 다루려면 adapter 로의 역방향 의존이 생기므로
 * P1-D-α+β 에서 application/dto 로 물리 이동했다 (연기 목록 #7 해소). JPA 매핑은
 * {@code @Enumerated(EnumType.STRING)} 로 이 enum 을 그대로 참조한다.</p>
 */
public enum WorkflowStep {
    REQUESTED,
    PREPARING,
    GENERATING,
    COMPLETED,
    FAILED
}
