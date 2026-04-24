package com.gearshow.backend.showcase.application.port.in;

/**
 * Tripo SUCCESS 인지 이후 결과 파일을 S3 에 미러링하고 워크플로우를 COMPLETED 로 전이하는 유스케이스.
 *
 * <p>{@code TripoSuccessEventListener} 가 Poller 발행 이벤트를 받아 호출한다. "언제" 호출할지는
 * Listener(Adapter) 가, "무엇을" 할지는 이 유스케이스(Application) 가 담당한다.</p>
 *
 * <p>설계 §7 [8] — Downloader. 흐름:</p>
 * <ol>
 *   <li>[락 밖] Tripo GET → 결과 파일 다운로드 → S3 PUT (결정적 key 로 멱등)</li>
 *   <li>[락 안] TX_final: {@code markCompleted} 조건부 UPDATE → Showcase3dModel 도메인 UPDATE
 *       → Outbox {@code MODEL_GENERATION_COMPLETED} 발행</li>
 * </ol>
 */
public interface DownloadAndMirrorUseCase {

    /**
     * 워크플로우의 Tripo 결과를 S3 에 미러링하고 TX_final 을 수행한다.
     *
     * <p>모든 예외는 내부에서 로그로 삼키고 정상 반환한다. stuck 은 Reconcile(P1-G) 이 복구.</p>
     *
     * @param workflowId   완료 대상 워크플로우 id
     * @param tripoTaskId  Tripo task_id (결과 다운로드에 재사용)
     */
    void download(Long workflowId, String tripoTaskId);
}
