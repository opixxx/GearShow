package com.gearshow.backend.showcase.application.port.out;

import java.time.Duration;

/**
 * Tripo 폴링 대상 워크플로우를 적응형 지연큐에 offer/take 하는 포트 (설계 §3.4 {@code poll:delayed-queue}).
 *
 * <p>설계 §6.2 에 따라 Worker 가 TX2 (PREPARING → GENERATING) 에 성공한 뒤 30초 delay 로 offer 하고,
 * Poller 단일 스레드가 {@link #take()} 로 블로킹 대기한다. {@code IN_PROGRESS} 응답 시 다시 30초
 * delay 로 re-queue 해 자동 적응형 폴링을 구성한다.</p>
 *
 * <p>구현체가 없는 환경 (예: 단일 인스턴스 로컬/테스트) 에서는 Bean 이 등록되지 않아 Poller 전체가
 * 비활성화된다. 호출자는 {@link org.springframework.beans.factory.ObjectProvider} 등으로 안전 조회
 * 패턴을 사용해야 한다.</p>
 */
public interface WorkflowPollQueuePort {

    /**
     * 워크플로우를 지연큐에 추가한다. {@code delay} 경과 후 {@link #take()} 에서 방출된다.
     *
     * @param workflowId 대상 워크플로우 id
     * @param delay      방출까지의 지연 시간
     */
    void offer(Long workflowId, Duration delay);

    /**
     * 지연큐에서 워크플로우 id 를 꺼낸다. 원소가 없으면 블로킹 대기한다.
     *
     * @return delay 가 경과한 워크플로우 id
     * @throws InterruptedException 대기 중 인터럽트 발생
     */
    Long take() throws InterruptedException;
}
