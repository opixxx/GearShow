package com.gearshow.backend.showcase.application.dto;

/**
 * 쇼케이스 상세 응답용 3D 모델 상태 (ADR-010 프로세스 테이블 분리 후 유도 값).
 *
 * <p>엔티티 컬럼이 아니라 {@code model_generation_workflow.current_step} +
 * {@code showcase_3d_model} 존재 여부로부터 파생된다. Flutter 클라이언트가 기존에 쓰던
 * 응답 계약을 하위호환으로 유지하기 위해 도메인/프로세스 분리 이후에도 동일한 enum 값을
 * 노출한다.</p>
 *
 * <p><b>유도 규칙</b> ({@code ShowcaseModelStatusResolver}):</p>
 * <ul>
 *   <li>workflow 없음 + Showcase3dModel 없음 → {@link #NONE}</li>
 *   <li>workflow.currentStep ∈ {REQUESTED, PREPARING, GENERATING} → {@link #GENERATING}</li>
 *   <li>workflow.currentStep = COMPLETED → {@link #COMPLETED}</li>
 *   <li>workflow.currentStep = FAILED → {@link #FAILED}</li>
 * </ul>
 */
public enum ShowcaseModelStatus {

    /** 3D 모델 요청 이력 자체가 없음 (쇼케이스는 있지만 modelSourceImages 미설정 등). */
    NONE,

    /** 워크플로우 진행 중 (REQUESTED/PREPARING/GENERATING 통합). */
    GENERATING,

    /** 완성품 존재. */
    COMPLETED,

    /** 워크플로우 FAILED 종결. 사용자 재시도 또는 운영 조사 대상. */
    FAILED
}
