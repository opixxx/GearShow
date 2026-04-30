package com.gearshow.backend.showcase.application.port.in;

/**
 * 3D 모델 완성 후 Showcase 의 비정규화 플래그 {@code has_3d_model} 을 {@code true} 로 동기화하는
 * 입력 포트.
 *
 * <p>호출 경로: {@code Model3dCompletedEvent} → {@code Model3dCompletedEventListener}
 * → 본 UseCase. AFTER_COMMIT 리스너에서 호출되므로 {@code DownloadFinalizer} 의 TX_final 이
 * 커밋되어야만 실행된다.</p>
 *
 * <p>비정규화 read-model 갱신 성격이라 도메인 aggregate 로드 사이클을 거치지 않고
 * 단일 컬럼 UPDATE 로 처리한다 ({@code UPDATE has_3d_model = true} 는 멱등).</p>
 */
public interface MarkShowcaseHas3dModelUseCase {

    /**
     * 지정 Showcase 의 {@code has_3d_model} 플래그를 {@code true} 로 갱신한다.
     *
     * @param showcaseId 대상 Showcase ID
     */
    void markHas3dModel(Long showcaseId);
}
