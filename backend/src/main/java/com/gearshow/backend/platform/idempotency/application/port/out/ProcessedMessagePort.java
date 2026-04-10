package com.gearshow.backend.platform.idempotency.application.port.out;

import java.time.Instant;

/**
 * 처리된 메시지 이력 저장소 Outbound Port.
 *
 * <p>멱등성 보장을 위한 단순 인프라 개념이므로 도메인 모델 없이
 * 기본 타입만으로 메시지 ID를 다룬다.</p>
 */
public interface ProcessedMessagePort {

    /**
     * 처리 이력을 저장한다. 동일한 (messageId, domain)이 이미 존재하면 저장하지 않는다.
     *
     * <p>예외를 던지지 않고 boolean으로 결과를 반환하므로,
     * Spring 트랜잭션의 rollback-only 함정을 회피할 수 있다.</p>
     *
     * @param messageId 메시지 고유 식별자
     * @param domain    도메인 이름
     * @return 새로 저장되었으면 {@code true}, 이미 존재했으면 {@code false}
     */
    boolean saveIfAbsent(String messageId, String domain);

    /**
     * 지정 시각보다 이전에 처리된 이력을 배치 단위로 삭제한다.
     *
     * <p>한 번에 모든 행을 삭제하면 락 경합이 발생하므로 배치 단위로 분할 삭제한다.
     * 호출자(스케줄러)가 반복 호출하여 전체 정리를 완료한다.</p>
     *
     * @param threshold 이 시각 이전 이력을 삭제
     * @param batchSize 한 번에 삭제할 최대 행 수
     * @return 이번 호출에서 삭제된 행 수 (batchSize 미만이면 더 이상 삭제할 행 없음)
     */
    int deleteBatchOlderThan(Instant threshold, int batchSize);
}
