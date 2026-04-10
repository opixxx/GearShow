package com.gearshow.backend.platform.outbox.application.port.in;

/**
 * 발행 완료된 Outbox 메시지 정리 유스케이스 (Inbound Port).
 *
 * <p>발행 완료된 레코드는 감사 로그 목적으로 일정 기간 보존하다가
 * 보존 기간을 초과하면 삭제한다. 이력 테이블이 무한히 쌓이는 것을 방지한다.</p>
 */
public interface CleanupOutboxUseCase {

    /**
     * 보존 기간을 초과한 발행 완료 메시지를 삭제한다.
     *
     * @return 삭제된 총 행 수
     */
    int cleanup();
}
