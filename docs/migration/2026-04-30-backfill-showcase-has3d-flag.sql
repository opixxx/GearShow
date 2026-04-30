-- ============================================================================
-- Showcase.has_3d_model 비정규화 플래그 백필
-- ============================================================================
-- 사유: PR 머지 이전에 3D 모델 생성이 완료된 쇼케이스는 has_3d_model 이 false 로
--       남아 있어 목록 응답에서 3D 뱃지가 표시되지 않는다. showcase_3d_model 테이블
--       에 행이 존재하면 = "3D 모델 생성 완료" 이므로, 그 기준으로 일괄 보정한다.
--
-- 적용 시점: 코드 머지 후 첫 배포 직전 (또는 직후). 신규 완료 흐름은 코드가
--           처리하므로 중복 실행돼도 무해 (멱등 — `AND has_3d_model = FALSE` 가드).
--
-- 영향 범위: showcase 테이블 — has_3d_model = true 로 갱신되는 행만 변경.
--          이미 true 인 행이나 DELETED 상태는 건너뜀 (showcase_status 조건).
-- ============================================================================

-- ========================================================================
-- 1. 사전 점검 (반드시 먼저 실행)
-- ========================================================================
-- (a) 갱신 대상 행 수 확인
--     1만 행 미만 → 단일 트랜잭션 OK
--     1만 ~ 10만 행 → 트래픽 한산 시간대 권장
--     10만 행 초과 → 청크 분할 필수 (아래 §3 참고)
SELECT COUNT(*) AS rows_to_update
  FROM showcase s
  JOIN showcase_3d_model m ON m.showcase_id = s.id
 WHERE s.has_3d_model = FALSE
   AND s.showcase_status <> 'DELETED';

-- (b) EXPLAIN 으로 인덱스 활용 확인
--     m.showcase_id 가 unique index lookup, s 가 PK eq_ref 인지 확인.
EXPLAIN
UPDATE showcase s
       JOIN showcase_3d_model m ON m.showcase_id = s.id
   SET s.has_3d_model = TRUE
 WHERE s.has_3d_model = FALSE
   AND s.showcase_status <> 'DELETED';

-- ========================================================================
-- 2. 백필 실행 (단일 트랜잭션 — < 10만 행)
-- ========================================================================
-- IN (SELECT) 대신 JOIN 사용: MySQL 8 옵티마이저 선택 안정성 + EXPLAIN 가독성.
-- showcase_3d_model.showcase_id 의 unique index 가 nested-loop join 으로 풀린다.
-- 두 번 실행해도 ROW_COUNT() = 0 (멱등 — has_3d_model = FALSE 가드).

START TRANSACTION;

UPDATE showcase s
       JOIN showcase_3d_model m ON m.showcase_id = s.id
   SET s.has_3d_model = TRUE
 WHERE s.has_3d_model = FALSE
   AND s.showcase_status <> 'DELETED';

COMMIT;

-- ========================================================================
-- 3. (옵션) 청크 분할 — §1 사전 점검에서 10만 행 초과 시
-- ========================================================================
-- binlog / undo log 압박 완화. 운영 스크립트(bash + mysql client) 또는 stored
-- procedure 로 풀어 실행한다. 아래는 procedural pseudo-code 예시.
--
-- DELIMITER $$
-- CREATE PROCEDURE backfill_has_3d_model_chunked()
-- BEGIN
--   REPEAT
--     UPDATE showcase s
--            JOIN showcase_3d_model m ON m.showcase_id = s.id
--        SET s.has_3d_model = TRUE
--      WHERE s.has_3d_model = FALSE
--        AND s.showcase_status <> 'DELETED'
--      LIMIT 5000;
--     -- 각 청크 사이에 짧은 sleep — replication lag / 동시 트래픽 완화
--     SELECT SLEEP(0.1);
--   UNTIL ROW_COUNT() = 0 END REPEAT;
-- END$$
-- DELIMITER ;
-- CALL backfill_has_3d_model_chunked();
-- DROP PROCEDURE backfill_has_3d_model_chunked;

-- ============================================================================
-- 롤백 SQL (운영상 필요할 때만)
-- ============================================================================
-- 본 백필을 되돌리려면 아래 SQL 을 실행한다. 단 운영 환경에서는 has_3d_model = true
-- 가 의도된 상태이므로 일반적으로 롤백할 이유가 없다. 코드 회귀가 발생해 플래그
-- 갱신 경로가 깨진 상황에서만 검토.
--
-- START TRANSACTION;
-- UPDATE showcase s
--        JOIN showcase_3d_model m ON m.showcase_id = s.id
--    SET s.has_3d_model = FALSE;
-- COMMIT;
