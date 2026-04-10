package com.gearshow.backend.platform.idempotency.domain;

/**
 * 멱등성 도메인 키.
 *
 * <p>processed_message 테이블의 도메인 컬럼 값으로 사용되며,
 * 각 Kafka Consumer는 자신의 도메인 키로 멱등성을 격리한다.</p>
 *
 * <p>새 도메인이 추가되면 이 enum에 항목을 추가해야 한다.
 * 컴파일 타임에 오타를 잡을 수 있고, 사용처를 한눈에 파악할 수 있다.</p>
 */
public enum IdempotencyDomain {

    /** 3D 모델 생성 요청 처리 (Showcase 도메인) */
    SHOWCASE_MODEL_GENERATION;
}
