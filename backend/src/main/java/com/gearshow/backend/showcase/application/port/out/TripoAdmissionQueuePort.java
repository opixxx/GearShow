package com.gearshow.backend.showcase.application.port.out;

import java.util.Optional;

/**
 * 3D 생성 동시 작업 cap 초과 시 워크플로우를 대기시키는 입장 큐 포트 (ADR-025, 설계 §3.4 {@code tripo:queue}).
 *
 * <p>{@code poll:delayed-queue}(GENERATING 적응형 폴링) 와는 <b>별개 키·별개 목적</b>이다.
 * 본 큐는 {@code PrepareWorkflowService} 의 입장 게이트가 활성 작업 수(PREPARING+GENERATING) 가
 * cap 이상일 때 워크플로우를 park 하고, 주기 drainer / Reconcile 이 슬롯 여유 시 가장 오래된
 * 항목부터 꺼내 재투입하는 score 기반 정렬 집합(ZSET)이다.</p>
 *
 * <p>score = enqueue epoch millis. 가장 작은 score = 가장 오래 대기 = FIFO 공정성.
 * Redis 가 필수 인프라(ADR-013) 이므로 구현체는 무조건 빈으로 등록된다.</p>
 */
public interface TripoAdmissionQueuePort {

    /**
     * 워크플로우를 입장 큐에 추가한다. 이미 존재하면 <b>최초 score 를 유지</b>해 FIFO 공정성을
     * 보존한다(중복 park 가 대기 순서를 뒤로 밀지 않게 한다).
     *
     * @param workflowId      대상 워크플로우 id
     * @param scoreEpochMillis 정렬 score (enqueue 시각, epoch millis)
     * @return 신규로 추가됐으면 {@code true}, 이미 존재해 score 를 유지했으면 {@code false}
     */
    boolean parkIfAbsent(long workflowId, long scoreEpochMillis);

    /**
     * 가장 작은 score(가장 오래 대기한) 워크플로우 id 를 atomic 하게 꺼낸다.
     *
     * @return 큐가 비어있으면 {@link Optional#empty()}, 아니면 꺼낸 워크플로우 id
     */
    Optional<Long> pollOldest();

    /** 현재 입장 큐에 대기 중인 원소 수. */
    long size();

    /** 지정 워크플로우가 입장 큐에 대기 중인지 여부 (Reconcile 의 이중 처리 방어용). */
    boolean contains(long workflowId);
}
