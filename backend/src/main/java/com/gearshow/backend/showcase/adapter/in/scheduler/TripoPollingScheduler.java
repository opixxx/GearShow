package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.port.in.PollGenerationStatusUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tripo 생성 상태 폴링 스케줄러 (Inbound Adapter).
 *
 * <p>주기적으로 {@link PollGenerationStatusUseCase#pollOnce()} 를 호출한다.
 * Worker 가 Tripo task 만 생성한 뒤 반환하고, 이 스케줄러가 완료/실패/타임아웃을 처리하는
 * "폴링 분리 아키텍처" 의 핵심 컴포넌트다.</p>
 *
 * <p>Kafka 비활성 환경(로컬/테스트)에서도 Tripo 를 흉내 낼 수 있도록 이 스케줄러는
 * 조건부 생성이 아니다 (FakeModelGenerationClient 로 동작). 다만 DB 에 GENERATING 상태의
 * 레코드가 없으면 pollOnce() 는 no-op 이므로 부하가 없다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripoPollingScheduler {

    private final PollGenerationStatusUseCase pollGenerationStatusUseCase;

    @Scheduled(fixedDelayString = "${app.tripo-polling.interval-ms:3000}")
    public void poll() {
        try {
            int terminal = pollGenerationStatusUseCase.pollOnce();
            if (terminal > 0) {
                log.debug("Tripo 폴링 사이클 완료 - terminal: {}", terminal);
            }
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않도록 예외를 포착하여 로그만 남긴다.
            log.error("Tripo 폴링 사이클 실행 중 예외 발생", e);
        }
    }
}
