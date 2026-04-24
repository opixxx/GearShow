package com.gearshow.backend.showcase.application.port.in;

/**
 * 3D 모델 생성 준비 유스케이스 (Inbound Port) — <b>레거시</b>.
 *
 * @deprecated P1-D-α+β 부터 Worker 경로는 {@link PrepareWorkflowUseCase} 를 호출한다.
 *     이 포트는 {@code Showcase3dModel} 기반 pre-P1 구현체({@code PrepareModelGenerationService})
 *     로 여전히 살아있지만 호출되지 않는다. {@code showcase_3d_model} 테이블의 프로세스 컬럼을
 *     축소하는 P1-F 에서 구현체와 함께 물리 제거 예정이다.
 */
@Deprecated(forRemoval = true)
public interface PrepareModelGenerationUseCase {

    /**
     * 3D 모델 생성을 시작할 준비를 한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @param showcaseId        쇼케이스 ID
     * @deprecated {@link PrepareWorkflowUseCase#prepare(Long)} 를 사용한다.
     */
    @Deprecated(forRemoval = true)
    void prepare(Long showcase3dModelId, Long showcaseId);
}
