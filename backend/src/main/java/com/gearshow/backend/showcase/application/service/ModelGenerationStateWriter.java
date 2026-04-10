package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3D 모델 상태 전이 + 쇼케이스 has3dModel 동기화를 단일 트랜잭션으로 묶는 전용 쓰기 컴포넌트.
 *
 * <p><b>왜 별도 컴포넌트인가</b>: Spring {@code @Transactional} 은 AOP 프록시 기반으로 동작하므로,
 * 같은 클래스 내에서 self-invocation(this 호출) 으로 호출하면 프록시를 우회하여 트랜잭션이 시작되지
 * 않는다 (Sonar {@code java:S6809}). {@link PollGenerationStatusService} 에서 폴링 orchestration
 * 과 상태 전이를 같은 클래스에 두면 이 제약에 걸리므로, 상태 전이만 떼어 별도 빈으로 만들어
 * Spring 이 프록시를 주입하도록 한다.</p>
 *
 * <p>이 컴포넌트의 메서드들은 "showcase_3d_model + showcase" 두 Aggregate 변경을 원자적으로
 * 커밋한다. 한쪽만 커밋되는 불일치 상태를 방지하는 것이 목적이다.</p>
 */
@Component
@RequiredArgsConstructor
public class ModelGenerationStateWriter {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ShowcasePort showcasePort;

    /**
     * 폴링 시각만 갱신한다. 단일 테이블 업데이트이므로 단순히 save 에 위임한다
     * (Adapter 레벨 @Transactional 로 충분).
     */
    public void markPolled(Showcase3dModel model) {
        showcase3dModelPort.save(model.markPolled());
    }

    /**
     * 모델 COMPLETED 전환 + showcase.has3dModel=true 동기화를 단일 트랜잭션으로 커밋한다.
     */
    @Transactional
    public void markCompleted(Showcase3dModel model, GenerationResult result) {
        Showcase3dModel completed = model.complete(result.modelFileUrl(), result.previewImageUrl());
        showcase3dModelPort.save(completed);
        showcasePort.updateHas3dModel(model.getShowcaseId(), true);
    }

    /**
     * 모델 FAILED 전환 + showcase.has3dModel=false 동기화를 단일 트랜잭션으로 커밋한다.
     */
    @Transactional
    public void markFailed(Showcase3dModel model, String reason) {
        showcase3dModelPort.save(model.fail(reason));
        showcasePort.updateHas3dModel(model.getShowcaseId(), false);
    }
}
