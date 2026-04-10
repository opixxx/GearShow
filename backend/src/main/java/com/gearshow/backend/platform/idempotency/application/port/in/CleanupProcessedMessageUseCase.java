package com.gearshow.backend.platform.idempotency.application.port.in;

/**
 * 처리된 메시지 이력 정리 유스케이스.
 *
 * <p>오래된 멱등성 이력을 일괄 삭제하여 테이블 크기가 무한히 증가하지 않도록 한다.
 * 트리거(스케줄러, API 등)와 무관하게 비즈니스 로직만 정의한다.</p>
 */
public interface CleanupProcessedMessageUseCase {

    /**
     * 보존 기간이 지난 이력을 삭제한다.
     *
     * @return 삭제된 행 수
     */
    int cleanup();
}
