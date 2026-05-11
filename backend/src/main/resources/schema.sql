-- =============================================================================
-- GearShow schema.sql — ddl-auto: update 가 자동 반영 못하는 정의 변경 모음.
--
-- 정책: spring.sql.init.mode=always + jpa.defer-datasource-initialization=true
-- 설정으로 부팅 시 ddl-auto 직후 자동 실행된다. 모든 SQL 은 멱등 — 매 부팅마다
-- 동일 정의 재적용 시 no-op.
--
-- 운영자 수동 SQL 0. 본 파일이 schema 변경의 single source of truth.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ADR-016 §D1: StudType enum 에 MG, HG 추가 (5개 → 7개). PR #84 머지본 복원.
--
-- 배경: ddl-auto: update 는 ENUM 컬럼의 enum 값 변경을 자동 반영하지 않으므로
-- Java 의 StudType enum 은 7개로 갱신됐지만 DB 의 boots_spec.stud_type 은
-- 이전 5개('AG','FG','IC','SG','TF') 정의로 남아있다. Hibernate 가 'MG'/'HG' 를
-- INSERT 시 'Data truncated for column' 으로 실패 (PR #82 PoC 검증에서 발견).
-- 기존 5개 enum 값 보존 + 2개 추가 → backward compatible (기존 행 영향 0).
-- -----------------------------------------------------------------------------
ALTER TABLE boots_spec MODIFY COLUMN stud_type
  ENUM('FG','SG','AG','TF','IC','MG','HG') NOT NULL;

-- -----------------------------------------------------------------------------
-- ADR-016 §D3: kit_type nullable. PR #82 PoC (uniform 적재) 의 INTERNAL_ERROR 1건
-- (HM3206-045 — Jordan x PSG 2025/26 4th kit) 으로 발견된 마이그레이션 누락 보강.
--
-- 배경: ADR-016 §D3 가 빈티지/4th kit 등 시장 표기 미명시 케이스를 위해 nullable
-- 결정. Java JpaEntity 는 nullable=true 로 갱신됐으나 DB ENUM 컬럼은 NOT NULL.
-- (PR #84 stud_type 패턴 동일.)
-- 기존 3개 enum 값 보존 + NULL 허용 → backward compatible (기존 행 영향 0).
-- -----------------------------------------------------------------------------
ALTER TABLE uniform_spec MODIFY COLUMN kit_type
  ENUM('AWAY','HOME','THIRD') NULL;

-- -----------------------------------------------------------------------------
-- ADR-022: silos.yaml 시리즈 통일에 따른 운영 catalog 의 silo_name backfill.
--
-- 정책 변경 (2026-05-08): 라인 단위(`Phantom GX`, `Tiempo Legend`) → 시리즈 단위(`Phantom`, `Tiempo`).
-- 멱등 — 이미 시리즈 통일된 행은 빈 WHERE 결과로 0 rows affected (no-op).
-- 자세한 결정 근거는 ADR-022 §Decision 참조.
-- -----------------------------------------------------------------------------

-- Nike: Mercurial Superfly + Mercurial Vapor → Mercurial
UPDATE boots_spec SET silo_name = 'Mercurial', silo_name_ko = '머큐리얼'
  WHERE silo_name IN ('Mercurial Superfly', 'Mercurial Vapor');

-- Nike: Phantom GX + Phantom Luna (+ Phantom GT) → Phantom
UPDATE boots_spec SET silo_name = 'Phantom', silo_name_ko = '팬텀'
  WHERE silo_name IN ('Phantom GX', 'Phantom Luna', 'Phantom GT');

-- Nike: Tiempo Legend → Tiempo
UPDATE boots_spec SET silo_name = 'Tiempo', silo_name_ko = '티엠포'
  WHERE silo_name = 'Tiempo Legend';

-- Adidas: Copa Pure + Copa Mundial → Copa
UPDATE boots_spec SET silo_name = 'Copa', silo_name_ko = '코파'
  WHERE silo_name IN ('Copa Pure', 'Copa Mundial');

-- Adidas: X Crazyfast + X Speedportal → Adidas X
UPDATE boots_spec SET silo_name = 'Adidas X', silo_name_ko = '아디다스 X'
  WHERE silo_name IN ('X Crazyfast', 'X Speedportal');

-- Mizuno: Morelia Neo + Morelia II → Morelia
UPDATE boots_spec SET silo_name = 'Morelia', silo_name_ko = '모렐리아'
  WHERE silo_name IN ('Morelia Neo', 'Morelia II');
