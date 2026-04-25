package com.gearshow.backend.showcase.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 쇼케이스 3D 모델 생성 파이프라인의 Kafka 토픽 선언.
 *
 * <p><b>역할 경계</b>: 이 클래스는 {@code showcase} Bounded Context 의 "어떤 토픽이 존재하고
 * 어떤 이름을 갖는가" 만 담당한다. 범용 Producer/Serializer 같은 Kafka 인프라 구성은
 * {@code platform/outbox/infrastructure/config/OutboxKafkaConfig} 가 분리해서 관리한다.
 * 이유: 재사용 가능한 인프라 (platform) 와 BC 특화 계약 (showcase) 의 응집도 분리.</p>
 */
@Configuration
public class ShowcaseKafkaTopicConfig {

    /** 3D 모델 생성 요청 토픽 (메인) */
    public static final String MODEL_GENERATION_REQUEST_TOPIC = "showcase.model-generation.request";
    /**
     * 3D 모델 생성 요청 재시도 토픽.
     *
     * <p>P1-D 에서 {@code @RetryableTopic(fixedDelayTopicStrategy=SINGLE_TOPIC)} 방식으로
     * 소비자 재시도 대상이 된다. 이 PR 에선 토픽 예약 등록만 수행하고 소비자 연결은 P1-D 에서 한다.
     * 파티션/복제본은 메인 토픽과 동일(3/1) — key(쇼케이스 기준) 파티셔닝이 메인·retry·DLT 간
     * 일관되어야 재시도 후에도 동일 쇼케이스 메시지의 순서가 보존된다.</p>
     */
    public static final String MODEL_GENERATION_REQUEST_RETRY_TOPIC =
            MODEL_GENERATION_REQUEST_TOPIC + "-retry";
    /** 3D 모델 생성 요청 DLT (재시도 소진된 메시지 격리) */
    public static final String MODEL_GENERATION_REQUEST_DLT_TOPIC =
            MODEL_GENERATION_REQUEST_TOPIC + ".DLT";

    /**
     * 3D 모델 생성 완료 이벤트 토픽.
     *
     * <p>Downloader 의 TX_final 이 Outbox 에 이 토픽 이름으로 이벤트를 적재한다 (P1-F). 현재는
     * Consumer 가 없으며 후속 P1-H 알림 단계에서 FCM 푸시 Consumer 가 붙을 예정이다.
     * 파티션/복제본은 request 토픽과 동일 (3/1) — 쇼케이스 단위 순서 보장.</p>
     */
    public static final String MODEL_GENERATION_COMPLETED_TOPIC =
            "showcase.model-generation.completed";

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationRequestTopic() {
        return TopicBuilder.name(MODEL_GENERATION_REQUEST_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 재시도 토픽 — 소비자 재시도 대상. 파티션/복제본은 메인과 동일해야 파티션 키에 의해
     * 동일 쇼케이스 메시지가 항상 같은 파티션으로 유지된다(순서 보존).
     */
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationRequestRetry() {
        return TopicBuilder.name(MODEL_GENERATION_REQUEST_RETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Topic — {@code DefaultErrorHandler} 의 재시도가 모두 소진된 메시지가 이동한다.
     * 운영 중 수동 확인/재처리 대상이다.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationRequestDlt() {
        return TopicBuilder.name(MODEL_GENERATION_REQUEST_DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationCompletedTopic() {
        return TopicBuilder.name(MODEL_GENERATION_COMPLETED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
