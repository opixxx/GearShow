package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.application.port.out.ProcessedMessagePort;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 멱등성 처리 권한 획득 유스케이스 구현체.
 *
 * <p>{@code (messageId, domain)} 복합 UNIQUE 제약을 활용해 동시 접근을 원자적으로 차단한다.
 * 리밸런싱 등으로 같은 메시지가 여러 워커에 동시 전달되어도 단 하나만 처리되도록 보장한다.</p>
 *
 * <p>구현 노트:</p>
 * <ul>
 *     <li>INSERT IGNORE 네이티브 쿼리를 사용하므로 예외가 발생하지 않아
 *         Spring 트랜잭션의 rollback-only 함정을 회피한다.</li>
 *     <li>REQUIRES_NEW 트랜잭션이 필요 없어 커넥션 점유 부담이 줄어든다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcquireIdempotencyService implements AcquireIdempotencyUseCase {

    private final ProcessedMessagePort processedMessagePort;

    @Override
    public boolean tryAcquire(String messageId, IdempotencyDomain domain) {
        boolean acquired = processedMessagePort.saveIfAbsent(messageId, domain.name());
        if (!acquired) {
            log.info("이미 처리된 메시지 - messageId: {}, domain: {}", messageId, domain);
        }
        return acquired;
    }
}
