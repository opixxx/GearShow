package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;

/**
 * 쇼케이스 상세 응답용 3D 모델 상태 유도 (ADR-010 Q4-(1)).
 *
 * <p>Showcase3dModel 완성품 존재 여부 + model_generation_workflow.current_step 을 입력으로
 * 받아 Flutter 클라이언트 응답 계약용 {@link ShowcaseModelStatus} 를 결정한다.</p>
 *
 * <p><b>우선순위</b>:</p>
 * <ol>
 *   <li>완성품 존재 → {@link ShowcaseModelStatus#COMPLETED}</li>
 *   <li>workflow.currentStep = FAILED → {@link ShowcaseModelStatus#FAILED}</li>
 *   <li>workflow.currentStep ∈ {REQUESTED, PREPARING, GENERATING} → {@link ShowcaseModelStatus#GENERATING}</li>
 *   <li>workflow.currentStep = COMPLETED (완성품 누락된 일시적 상태) → {@link ShowcaseModelStatus#COMPLETED}</li>
 *   <li>workflow 없음 → {@link ShowcaseModelStatus#NONE}</li>
 * </ol>
 */
public final class ShowcaseModelStatusResolver {

    private ShowcaseModelStatusResolver() {
    }

    public static ShowcaseModelStatus resolve(boolean hasCompletedArtifact, WorkflowStep latestStep) {
        if (hasCompletedArtifact) {
            return ShowcaseModelStatus.COMPLETED;
        }
        if (latestStep == null) {
            return ShowcaseModelStatus.NONE;
        }
        return switch (latestStep) {
            case REQUESTED, PREPARING, GENERATING -> ShowcaseModelStatus.GENERATING;
            case COMPLETED -> ShowcaseModelStatus.COMPLETED;
            case FAILED -> ShowcaseModelStatus.FAILED;
        };
    }
}
