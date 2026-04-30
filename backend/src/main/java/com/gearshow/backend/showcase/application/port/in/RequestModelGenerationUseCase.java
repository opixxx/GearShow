package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;

import java.util.List;

/**
 * 3D 모델 생성 요청 유스케이스.
 */
public interface RequestModelGenerationUseCase {

    /**
     * 쇼케이스 등록 시 3D 모델 생성을 비동기로 요청한다.
     * 이미지는 클라이언트가 Presigned URL로 S3에 직접 업로드하고,
     * 서버는 S3 키 목록을 전달받아 DB에 저장한다.
     *
     * <p>{@code idempotencyKey} 는 API {@code Idempotency-Key} 헤더 값으로,
     * {@code model_generation_workflow.idempotency_key} UNIQUE 식별자와
     * Outbox {@code event_id} 결정적 파생의 입력이 된다 (ADR-011 ①③).</p>
     *
     * @param showcaseId           쇼케이스 ID
     * @param ownerId              요청자 ID (사용자별 일일 quota 카운터에 사용)
     * @param idempotencyKey       API 멱등성 키 (non-null, non-blank)
     * @param modelSourceImageKeys 소스 이미지 S3 키 목록 (앞/좌/뒤/우, 최소 4개)
     * @return 생성 요청 결과
     */
    ModelGenerationResult requestOnCreate(Long showcaseId,
                                          Long ownerId,
                                          String idempotencyKey,
                                          List<String> modelSourceImageKeys);

    /**
     * 3D 모델 생성을 재요청한다 (실패 후 재시도).
     *
     * <p>재시도는 {@code attempt_no} 를 증가시킨 새 워크플로우 행을 INSERT 하여
     * 이력을 보존한다 (ADR-010). {@code idempotencyKey} 는 호출자가 새로 생성한 값이어야 한다
     * — 이전 attempt 와 같은 키를 재사용하면 UNIQUE 제약 위반.</p>
     *
     * @param showcaseId           쇼케이스 ID
     * @param ownerId              요청자 ID
     * @param idempotencyKey       재시도용 새 멱등성 키 (non-null, non-blank)
     * @param modelSourceImageKeys 소스 이미지 S3 키 목록 (최소 4개)
     * @return 생성 요청 결과
     */
    ModelGenerationResult requestRetry(Long showcaseId,
                                       Long ownerId,
                                       String idempotencyKey,
                                       List<String> modelSourceImageKeys);
}
