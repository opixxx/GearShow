package com.gearshow.backend.showcase.application.port.out;

/**
 * 3D 모델 생성 외부 클라이언트 Port.
 *
 * <p><b>폴링 분리 아키텍처</b>에 맞춰 3단계 메서드로 분리되어 있다:</p>
 * <ol>
 *   <li>{@link #startGeneration(Long, Long)} — Worker 가 호출. 이미지 업로드 + Tripo task 생성.
 *       정확히 1회만 호출되어야 한다 (비용 보호).</li>
 *   <li>{@link #fetchStatus(String)} — 폴링 스케줄러가 주기적으로 호출. Tripo task 의 현재 상태 조회.</li>
 *   <li>{@link #fetchResult(String, Long)} — 폴링 스케줄러가 task success 확인 후 호출.
 *       GLB/프리뷰 다운로드 + S3 저장.</li>
 * </ol>
 */
public interface ModelGenerationClient {

    /**
     * 3D 모델 생성을 시작한다.
     *
     * <p>소스 이미지를 S3 에서 읽어 Tripo 에 업로드하고, Tripo task 를 생성한 뒤
     * 반환된 {@code task_id} 를 돌려준다. 이 단계가 유일한 비용 발생 지점이므로
     * 호출 후에는 반드시 DB 에 task_id 를 저장해야 한다.</p>
     *
     * @param showcase3dModelId 3D 모델 ID
     * @param showcaseId        쇼케이스 ID
     * @return Tripo task_id (외부 시스템 고유 식별자)
     */
    String startGeneration(Long showcase3dModelId, Long showcaseId);

    /**
     * 진행 중인 Tripo task 의 상태를 조회한다.
     * 폴링 스케줄러가 주기적으로 호출한다.
     */
    GenerationStatus fetchStatus(String taskId);

    /**
     * Tripo task 가 성공으로 완료된 경우, 결과 파일(GLB, 프리뷰)을
     * 다운로드하여 S3 에 저장하고 영구 URL 을 반환한다.
     *
     * @param taskId     Tripo task_id
     * @param showcaseId 쇼케이스 ID (S3 key 생성에 사용)
     */
    GenerationResult fetchResult(String taskId, Long showcaseId);

    /** Tripo task 현재 진행 단계. */
    enum GenerationPhase {
        /** 아직 처리 중 (폴링 계속 필요) */
        RUNNING,
        /** Tripo 가 생성 성공, 결과 다운로드 가능 */
        SUCCESS,
        /** Tripo 가 생성 실패 (이미지 품질 부족 등) */
        FAILED
    }

    /**
     * Tripo task 상태 조회 결과.
     *
     * <p>{@code record} 의 public 생성자는 모든 조합의 입력을 허용하므로, 상태 조합의
     * 불변식(예: SUCCESS 인데 failureReason 이 있는 모순) 은 compact constructor 에서 차단한다.
     * 외부에서 직접 생성하지 말고 {@link #running()} / {@link #success()} / {@link #failed(String)}
     * 정적 팩토리를 사용해야 한다.</p>
     *
     * @param phase          현재 단계 (null 불가)
     * @param failureReason  FAILED 인 경우에만 필수, 그 외에는 null 이어야 함
     */
    record GenerationStatus(GenerationPhase phase, String failureReason) {

        public GenerationStatus {
            if (phase == null) {
                throw new IllegalArgumentException("phase 는 필수입니다");
            }
            if (phase == GenerationPhase.FAILED && (failureReason == null || failureReason.isBlank())) {
                throw new IllegalArgumentException("FAILED 상태는 failureReason 이 필수입니다");
            }
            if (phase != GenerationPhase.FAILED && failureReason != null) {
                throw new IllegalArgumentException(
                        "FAILED 가 아닌 상태에는 failureReason 을 설정할 수 없습니다: " + phase);
            }
        }

        public boolean isRunning() {
            return phase == GenerationPhase.RUNNING;
        }

        public boolean isSuccess() {
            return phase == GenerationPhase.SUCCESS;
        }

        public boolean isFailed() {
            return phase == GenerationPhase.FAILED;
        }

        public static GenerationStatus running() {
            return new GenerationStatus(GenerationPhase.RUNNING, null);
        }

        public static GenerationStatus success() {
            return new GenerationStatus(GenerationPhase.SUCCESS, null);
        }

        public static GenerationStatus failed(String reason) {
            return new GenerationStatus(GenerationPhase.FAILED, reason);
        }
    }

    /** 결과 다운로드 + S3 저장 완료된 최종 산출물. */
    record GenerationResult(
            String modelFileUrl,
            String previewImageUrl
    ) {
    }
}
