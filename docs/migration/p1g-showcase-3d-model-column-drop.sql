-- ============================================================================
-- P1-G 운영 마이그레이션 — 3D 파이프라인 도메인/프로세스 테이블 분리 반영 (ADR-010)
-- ============================================================================
-- 본 스크립트는 운영 환경에 수동 적용한다. 로컬/테스트는 JPA ddl-auto 가 자동 처리하므로
-- 별도 실행 불필요.
--
-- 적용 순서 (트랜잭션 안에서):
--   1. showcase_3d_model 프로세스 컬럼 제거
--   2. showcase_3d_model 관련 인덱스 제거
--   3. model_source_image FK 컬럼 rename (showcase_3d_model_id → showcase_id)
--   4. 신규 인덱스 추가
--
-- 롤백 전략:
--   revert 전 반드시 `mysqldump` 백업. 이미 삭제된 컬럼 값은 복구 불가.
--   롤백 SQL 은 파일 하단 주석 참조.
-- ============================================================================

START TRANSACTION;

-- 1. 프로세스 컬럼 사용 인덱스 먼저 제거 (외래 종속 회피)
ALTER TABLE showcase_3d_model
    DROP INDEX idx_3d_model_status_polled;
ALTER TABLE showcase_3d_model
    DROP INDEX idx_3d_model_status_requested;

-- 2. 프로세스 컬럼 제거 (완성품만 보존)
ALTER TABLE showcase_3d_model
    DROP COLUMN model_status,
    DROP COLUMN generation_provider,
    DROP COLUMN generation_task_id,
    DROP COLUMN requested_at,
    DROP COLUMN last_polled_at,
    DROP COLUMN failure_reason,
    DROP COLUMN retry_count;

-- 2-1. 완성품 보존을 위한 제약 강화
ALTER TABLE showcase_3d_model
    MODIFY COLUMN model_file_url VARCHAR(512) NOT NULL,
    MODIFY COLUMN generated_at   TIMESTAMP(6) NOT NULL,
    ADD COLUMN   updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6);

-- 3. model_source_image — Showcase3dModel 이 완성품 전용으로 바뀌며 요청 시점에 존재하지 않는다.
--    FK 를 showcase_id 로 직접 참조하도록 리네임한다.
ALTER TABLE model_source_image
    CHANGE COLUMN showcase_3d_model_id showcase_id BIGINT NOT NULL;

-- 4. 신규 인덱스 — 쇼케이스 단위 sort_order 조회 최적화
ALTER TABLE model_source_image
    ADD INDEX idx_msi_showcase_sort (showcase_id, sort_order);

-- 5. model_generation_workflow — Reconcile findStuckGenerating* 쿼리 인덱스 최적화.
--    기존 2컬럼 인덱스가 `IS NULL` / `IS NOT NULL` 조건을 커버하지 못해 filter + filesort 가 발생했다.
--    3컬럼 복합 인덱스로 교체해 range scan 만으로 stuck 후보를 뽑는다.
ALTER TABLE model_generation_workflow
    DROP INDEX idx_mgw_step_tripo_succeeded,
    DROP INDEX idx_mgw_step_last_polled,
    ADD INDEX idx_mgw_step_tripo_polled
        (current_step, tripo_succeeded_at, last_polled_at),
    ADD INDEX idx_mgw_step_tripo_heartbeat
        (current_step, tripo_succeeded_at, heartbeat_at);

COMMIT;

-- ============================================================================
-- 롤백 SQL (필요 시 — 데이터 손실 주의)
-- ============================================================================
-- START TRANSACTION;
-- ALTER TABLE model_source_image
--     DROP INDEX idx_msi_showcase_sort,
--     CHANGE COLUMN showcase_id showcase_3d_model_id BIGINT NOT NULL;
-- ALTER TABLE showcase_3d_model
--     DROP COLUMN updated_at,
--     MODIFY COLUMN model_file_url VARCHAR(512) NULL,
--     MODIFY COLUMN generated_at   TIMESTAMP(6) NULL,
--     ADD COLUMN model_status          VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
--     ADD COLUMN generation_provider   VARCHAR(64),
--     ADD COLUMN generation_task_id    VARCHAR(100),
--     ADD COLUMN requested_at          TIMESTAMP(6),
--     ADD COLUMN last_polled_at        TIMESTAMP(6),
--     ADD COLUMN failure_reason        VARCHAR(1024),
--     ADD COLUMN retry_count           INT NOT NULL DEFAULT 0;
-- ALTER TABLE showcase_3d_model
--     ADD INDEX idx_3d_model_status_polled    (model_status, last_polled_at, showcase_3d_model_id),
--     ADD INDEX idx_3d_model_status_requested (model_status, requested_at);
-- COMMIT;
