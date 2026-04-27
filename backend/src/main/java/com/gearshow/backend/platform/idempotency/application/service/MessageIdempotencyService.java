package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.in.MessageIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.application.port.out.ProcessedMessagePort;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 메시지 멱등성 처리 유스케이스 (ADR-011 §2.③) 의 구현체.
 *
 * <p><b>완료 시점 INSERT 패턴</b> — 비즈니스 처리가 정상 종료된 *후* {@link #markProcessed} 를
 * 호출하여 이력을 영구 기록한다. 시작 시점 선점 패턴은 워커가 처리 도중 크래시(OOM/SIGKILL)
 * 했을 때 보상 삭제가 일어나지 않아 재전송 메시지가 영구 스킵되므로 ADR-011 양보 불가 규칙에
 * 의해 금지된다.</p>
 *
 * <p>구현 노트:</p>
 * <ul>
 *     <li>{@link #markProcessed} 의 UNIQUE 충돌은 정상 시나리오로 간주하고 멱등 무시한다.
 *         두 워커가 같은 메시지를 동시 처리한 경우 둘 다 markProcessed 를 호출할 수 있으며,
 *         어느 쪽이 INSERT 하든 결과는 같다.</li>
 *     <li>INSERT IGNORE 네이티브 쿼리를 사용하므로 예외가 발생하지 않아
 *         Spring 트랜잭션의 rollback-only 함정을 회피한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageIdempotencyService implements MessageIdempotencyUseCase {

    private final ProcessedMessagePort processedMessagePort;

    @Override
    public boolean isProcessed(String messageId, IdempotencyDomain domain) {
        return processedMessagePort.existsByKey(messageId, domain.name());
    }

    @Override
    public void markProcessed(String messageId, IdempotencyDomain domain) {
        boolean newlyInserted = processedMessagePort.saveIfAbsent(messageId, domain.name());
        if (!newlyInserted) {
            log.debug("멱등성 이력 INSERT 충돌 — 이미 기록됨 (정상). messageId: {}, domain: {}",
                    messageId, domain);
        }
    }
}
