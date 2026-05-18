package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link TripoAdmissionQueuePort} 의 Redisson {@code RScoredSortedSet}(ZSET) 기반 구현
 * (ADR-025, 설계 §3.4 {@code tripo:queue}).
 *
 * <p>score = enqueue epoch millis. {@code pollFirst()} 가 최소 score(가장 오래 대기) 원소를
 * atomic 하게 제거·반환하므로 FIFO 공정성이 보장된다. {@code addIfAbsent} 는 이미 존재하면
 * score 를 갱신하지 않아 중복 park 가 대기 순서를 뒤로 밀지 않는다.</p>
 *
 * <p>{@code poll:delayed-queue}(적응형 폴링) 와는 별개 키({@code tripo:queue})·별개 목적이다.
 * Redis 가 GearShow 의 필수 인프라(ADR-013) 이므로 본 어댑터는 무조건 빈으로 등록된다 —
 * Noop/Fallback 어댑터를 두지 않는다. 부팅 시 Redis 미연결이면 ApplicationContext 가
 * 부팅에 실패한다(fail-fast).</p>
 */
@Slf4j
@Component
public class RedissonTripoAdmissionQueueAdapter implements TripoAdmissionQueuePort {

    static final String QUEUE_KEY = "tripo:queue";

    private final RScoredSortedSet<Long> queue;

    public RedissonTripoAdmissionQueueAdapter(RedissonClient redissonClient) {
        this.queue = redissonClient.getScoredSortedSet(QUEUE_KEY);
    }

    @Override
    public boolean parkIfAbsent(long workflowId, long scoreEpochMillis) {
        boolean added = queue.addIfAbsent((double) scoreEpochMillis, workflowId);
        if (added) {
            log.info("입장 큐 park — workflowId: {}, score: {}", workflowId, scoreEpochMillis);
        } else {
            log.debug("입장 큐 이미 대기 중 — 최초 score 유지. workflowId: {}", workflowId);
        }
        return added;
    }

    @Override
    public Optional<Long> pollOldest() {
        return Optional.ofNullable(queue.pollFirst());
    }

    @Override
    public long size() {
        return queue.size();
    }

    @Override
    public boolean contains(long workflowId) {
        return queue.contains(workflowId);
    }
}
