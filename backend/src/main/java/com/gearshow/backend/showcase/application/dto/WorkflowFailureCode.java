package com.gearshow.backend.showcase.application.dto;

/**
 * 3D 모델 생성 워크플로우 실패 코드.
 *
 * <p>실패 분류는 점진적으로 확장된다 — 본 PR(P1-D-α+β)은 TX1 경로에서 발생 가능한
 * 입력 검증성 실패만 정의한다. Tripo 관련 코드는 P1-D-γ, 복구/시간초과 관련 코드는
 * P1-D-δ/P1-G 에서 추가한다.</p>
 */
public enum WorkflowFailureCode {

    /** 소스 이미지 4장 요건 미충족 (DB 상태 불일치). */
    SOURCE_IMAGES_MISSING,

    /** 소스 이미지의 S3 객체가 존재하지 않음. 클라이언트 업로드 누락 또는 lifecycle 삭제. */
    S3_KEY_MISSING
}
