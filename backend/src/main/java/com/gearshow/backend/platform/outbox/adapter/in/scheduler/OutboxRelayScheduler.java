package com.gearshow.backend.platform.outbox.adapter.in.scheduler;

import com.gearshow.backend.platform.outbox.application.port.in.PublishOutboxUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox → Kafka Relay 스케줄러 (Inbound Adapter).
 *
 * <p>주기는 {@code app.outbox.relay-interval-ms} 로 설정한다 (기본 1초).
 * Kafka 가 비활성화된 환경에서는 빈 자체가 생성되지 않아 스케줄러도 동작하지 않는다.</p>
 *
 * <p><b>역할(api/worker) 분리 토글 (ADR-027)</b>: Outbox Relay 는 공용 플랫폼 인프라이므로
 * always-on 인 api 프로세스가 단독 소유한다. worker 프로세스는 {@code app.outbox.relay-enabled=false}
 * 로 이 빈을 비등록하여 2-프로세스 동시 relay(중복 발행) 를 차단한다. 미설정 시 활성(기본 {@code :true})
 * — 운영 무회귀(현 모놀리스 = 단일 프로세스 relay 유지). kafka 가드와 AND 결합한 형태는
 * {@code AdmissionQueueDrainScheduler} 의 기존 선례를 따른다.</p>
 */
@Slf4j
@Component
@ConditionalOnExpression(
        "${spring.kafka.enabled:false} and ${app.outbox.relay-enabled:true}")
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final PublishOutboxUseCase publishOutboxUseCase;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        try {
            int published = publishOutboxUseCase.publishPending();
            if (published > 0) {
                log.debug("Outbox Relay 실행 완료 - 발행 수: {}", published);
            }
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않도록 예외를 포착하여 로그만 남긴다.
            log.error("Outbox Relay 실행 중 예외 발생", e);
        }
    }
}
