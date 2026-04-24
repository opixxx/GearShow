package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.in.DownloadAndMirrorUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Tripo 결과 S3 미러링 + TX_final 수행 (설계 §7 [8]).
 *
 * <p><b>흐름</b>:</p>
 * <ol>
 *   <li>findSnapshot → GENERATING + tripoSucceededAt NOT NULL 인지 검증 (skip 조건)</li>
 *   <li>[락 밖] {@code modelGenerationClient.fetchResult} — Tripo GET + S3 PUT (결정적 key)</li>
 *   <li>[락 안] TX_final:
 *     <ul>
 *       <li>{@code workflowPort.markCompleted} affected=1 이면 계속, 0 이면 skip (재진입)</li>
 *       <li>Showcase3dModel UPDATE (modelFileUrl, previewImageUrl, generatedAt, modelStatus=COMPLETED)</li>
 *       <li>Outbox {@code MODEL_GENERATION_COMPLETED} 발행</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>양보 불가 규칙</b>:</p>
 * <ul>
 *   <li>Tripo GET / S3 PUT 은 <b>반드시 락 밖</b> (설계 §6.2)</li>
 *   <li>모든 예외는 삼키고 로그 — Reconcile(P1-G) 이 stuck 을 감지해 복구</li>
 *   <li>S3 key 는 {@code ModelGenerationClient} 구현이 결정적으로 유지 → 재실행 시 덮어쓰기 멱등</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadAndMirrorService implements DownloadAndMirrorUseCase {

    private final ModelGenerationWorkflowPort workflowPort;
    private final ModelGenerationClient modelGenerationClient;
    private final WorkflowLockPort workflowLockPort;
    /**
     * TX_final 을 단일 트랜잭션으로 수행하는 협력자. Spring AOP self-invocation 을 피하기 위해
     * 별도 컴포넌트로 분리되어 있으며, 호출 시점에 프록시가 {@code @Transactional} 을 적용한다.
     */
    private final DownloadFinalizer downloadFinalizer;

    @Override
    public void download(Long workflowId, String tripoTaskId) {
        Optional<WorkflowSnapshot> found = workflowPort.findSnapshot(workflowId);
        if (found.isEmpty()) {
            log.warn("워크플로우 조회 실패 — skip. workflowId: {}", workflowId);
            return;
        }
        WorkflowSnapshot snapshot = found.get();
        if (!eligibleForDownload(snapshot)) {
            return;
        }

        GenerationResult result;
        try {
            // [락 밖] Tripo GET + S3 PUT. 수 초~수십 초 걸릴 수 있어 workflow:lock 보유 금지.
            result = modelGenerationClient.fetchResult(tripoTaskId, snapshot.showcaseId());
        } catch (RuntimeException e) {
            log.error("Tripo 결과 다운로드 / S3 미러링 실패 — Reconcile 복구 대기. "
                    + "workflowId: {}, taskId: {}", workflowId, tripoTaskId, e);
            return;
        }

        finalizeUnderLock(snapshot, result);
    }

    private boolean eligibleForDownload(WorkflowSnapshot snapshot) {
        if (snapshot.currentStep() != WorkflowStep.GENERATING) {
            log.info("GENERATING 아님 — skip. workflowId: {}, currentStep: {}",
                    snapshot.id(), snapshot.currentStep());
            return false;
        }
        if (snapshot.tripoSucceededAt() == null) {
            log.info("tripo_succeeded_at 이 아직 null — Poller SUCCESS 인지 전. skip. workflowId: {}",
                    snapshot.id());
            return false;
        }
        return true;
    }

    private void finalizeUnderLock(WorkflowSnapshot snapshot, GenerationResult result) {
        Long workflowId = snapshot.id();
        try {
            workflowLockPort.withLock(workflowId, () ->
                    downloadFinalizer.finalizeDownload(workflowId, snapshot.showcaseId(), result));
        } catch (WorkflowLockBusyException e) {
            log.warn("TX_final 락 busy — 다른 Downloader 가 처리 중 추정. skip. "
                    + "workflowId: {}", workflowId);
        } catch (IllegalStateException e) {
            // DownloadFinalizer 가 Showcase3dModel 누락 같은 데이터 정합성 오류로 TX 롤백
            log.error("TX_final 데이터 정합성 오류 — Reconcile 복구 대기. workflowId: {}",
                    workflowId, e);
        } catch (org.springframework.dao.DataAccessException e) {
            // DB 일시 오류. S3 PUT 은 이미 수행됐으므로 재실행 시 덮어쓰기 멱등.
            // 로그만 남기고 Reconcile 이 복구한다.
            log.error("TX_final DB 실패 — Reconcile 복구 대기. workflowId: {}",
                    workflowId, e);
        }
    }
}
