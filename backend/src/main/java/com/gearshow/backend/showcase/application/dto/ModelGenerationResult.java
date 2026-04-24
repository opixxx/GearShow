package com.gearshow.backend.showcase.application.dto;

/**
 * 3D 모델 생성 요청 결과.
 *
 * <p>ADR-010 프로세스 테이블 분리 이후 workflow 단위 식별자 + 응답용 상태를 반환한다.</p>
 *
 * @param workflowId      신규로 INSERT 된 {@code model_generation_workflow.id}
 * @param modelStatus     응답용 파생 상태 — REQUESTED → {@link ShowcaseModelStatus#GENERATING}
 */
public record ModelGenerationResult(
        Long workflowId,
        ShowcaseModelStatus modelStatus
) {}
