-- ============================================================================
-- P1-G Aftermath 클린업 — DB 인덱스 + Outbox aggregate_type 정정
-- ============================================================================
-- 적용 순서:
--   1. model_generation_workflow.idx_mgw_step_created (current_step, created_at) 추가
--      — Reconcile.findStuckRequested 가 인덱스 range scan 하도록.
--   2. Outbox 미발행(PENDING) 행의 aggregate_type 'SHOWCASE_3D_MODEL' → 'SHOWCASE' 정정.
--
-- 주의: ALTER ADD INDEX 는 InnoDB online DDL 지원이지만 metadata lock 으로 수 ms~수백 ms stall 가능.
-- 트래픽 저점 시간대 적용 권장.
-- ============================================================================

START TRANSACTION;

ALTER TABLE model_generation_workflow
    ADD INDEX idx_mgw_step_created (current_step, created_at);

-- ADR-010 후속 정리: Outbox aggregate_type 의미 일치
-- Showcase3dModel 이 완성품 전용으로 축소돼 요청 시점 aggregate 식별자로 'SHOWCASE_3D_MODEL' 부적절.
-- 미발행 상태인 행만 'SHOWCASE' 로 정정한다 (이미 PUBLISHED 된 행은 history 보존, Consumer 는 aggregate_type
-- 으로 분기하지 않음).
UPDATE outbox_message
   SET aggregate_type = 'SHOWCASE'
 WHERE aggregate_type = 'SHOWCASE_3D_MODEL'
   AND published_at IS NULL;

COMMIT;

-- ============================================================================
-- 롤백 SQL
-- ============================================================================
-- ALTER TABLE model_generation_workflow DROP INDEX idx_mgw_step_created;
-- (aggregate_type 정정은 의미 회귀이므로 롤백 SQL 미제공 — 필요 시 변경 전 백업에서 복원)
