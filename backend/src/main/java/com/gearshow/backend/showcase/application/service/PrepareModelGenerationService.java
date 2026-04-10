package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.PrepareModelGenerationUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 3D 모델 생성 준비 서비스.
 *
 * <p>Worker 가 수신한 메시지에 대해 다음을 수행한다:</p>
 * <ol>
 *   <li>모델 상태 확인 (REQUESTED 아니면 skip — 재전달/중복 메시지 방어)</li>
 *   <li>Tripo startGeneration 호출 (이미지 업로드 + task 생성)</li>
 *   <li>task_id 와 함께 모델을 GENERATING 으로 전환</li>
 * </ol>
 *
 * <p>비즈니스 실패 시 예외를 던지지 않고 모델 상태만 전환한다:</p>
 * <ul>
 *   <li>Tripo Circuit Breaker OPEN → UNAVAILABLE (서비스 장애, 사용자 수동 재시도)</li>
 *   <li>Tripo 비즈니스 실패 ({@link RestClientException}) → FAILED (사용자에게 고정된 일반 메시지 노출)</li>
 * </ul>
 *
 * <p>다만 인프라 장애({@link DataAccessException} 계열 — DB 락 경합, 타임아웃 등) 는
 * 잡지 않고 그대로 던져서 Spring Kafka {@code DefaultErrorHandler} 가 DLT 로 보내도록 한다.
 * 인프라 장애를 비즈니스 실패로 오분류하면 사용자에게 잘못된 안내가 나가고 복구도 어렵다.</p>
 *
 * <p><b>실패 사유 메시지 정책</b>: {@code e.getMessage()} 를 그대로 저장하면 내부 URL,
 * 클래스명, 스택 일부가 사용자에게 노출될 위험이 있다. 카테고리화된 고정 한글 메시지를
 * 저장하고, 상세는 로그에만 남긴다.</p>
 *
 * <p><b>트랜잭션 범위</b>: 이 메서드는 의도적으로 전체를 트랜잭션으로 감싸지 않는다.
 * Tripo 외부 호출 동안 DB 커넥션을 점유하면 HikariCP 풀이 고갈되기 때문.
 * 각 DB 조작은 Showcase3dModelPort 구현체의 @Transactional 로 개별 커밋된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareModelGenerationService implements PrepareModelGenerationUseCase {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelGenerationClient modelGenerationClient;

    @Override
    public void prepare(Long showcase3dModelId, Long showcaseId) {
        Showcase3dModel model = showcase3dModelPort.findById(showcase3dModelId)
                .orElse(null);

        if (model == null) {
            log.warn("3D 모델을 찾을 수 없습니다 - showcase3dModelId: {}", showcase3dModelId);
            return;
        }

        if (model.getModelStatus() != ModelStatus.REQUESTED) {
            // 중복 메시지/재전달 시나리오: 이미 GENERATING/COMPLETED/FAILED 상태면 skip
            log.info("REQUESTED 상태가 아니어서 처리를 건너뜁니다 - showcase3dModelId: {}, status: {}",
                    showcase3dModelId, model.getModelStatus());
            return;
        }

        String taskId;
        try {
            taskId = modelGenerationClient.startGeneration(showcase3dModelId, showcaseId);
        } catch (CallNotPermittedException e) {
            // Tripo Circuit Breaker 가 OPEN — 서비스 일시 이용 불가.
            // 모델을 UNAVAILABLE 로 마킹하여 사용자가 수동 재시도할 수 있게 한다.
            log.warn("Tripo Circuit Breaker OPEN - 모델을 UNAVAILABLE 로 전환 - showcase3dModelId: {}",
                    showcase3dModelId);
            Showcase3dModel unavailable = model.markUnavailable("3D 생성 서비스가 일시적으로 이용 불가합니다");
            showcase3dModelPort.save(unavailable);
            return;
        } catch (DataAccessException e) {
            // 인프라 일시 장애는 DLT 로 보내 수동 재처리 (비즈니스 FAILED 와 구분)
            log.error("3D 모델 생성 시작 중 DB 장애 - showcase3dModelId: {}", showcase3dModelId, e);
            throw e;
        } catch (RestClientException e) {
            // Tripo API 호출 실패 (HTTP 네트워크/5xx/타임아웃 등) — 비즈니스 실패로 간주
            log.error("Tripo API 호출 실패 - showcase3dModelId: {}", showcase3dModelId, e);
            Showcase3dModel failed = model.fail("3D 모델 생성을 시작하지 못했습니다");
            showcase3dModelPort.save(failed);
            return;
        }

        // Tripo task 생성 성공 — 반드시 task_id 를 DB 에 보존해야 한다.
        // 이 save 가 실패하면 Tripo task 만 생성되고 우리는 task_id 를 잃는 edge case 가 발생하지만,
        // 예외를 그대로 던져 DLT 로 보내는 것이 정답이다 (비즈니스 FAILED 로 덮으면 복구가 더 어려워진다).
        Showcase3dModel generating = model.markGenerating(taskId);
        showcase3dModelPort.save(generating);
        log.info("3D 모델 생성 시작 - showcase3dModelId: {}, taskId: {}",
                showcase3dModelId, taskId);
    }
}
