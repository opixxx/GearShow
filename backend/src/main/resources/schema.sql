-- ADR-022: silos.yaml 시리즈 통일에 따른 운영 catalog 의 silo_name backfill.
--
-- 정책 변경 (2026-05-08): 라인 단위(`Phantom GX`, `Tiempo Legend`) → 시리즈 단위(`Phantom`, `Tiempo`).
-- 본 SQL 은 schema.sql 의 일부로 부팅 시 자동 실행되며, 멱등 — 이미 시리즈 통일된 행은
-- 빈 WHERE 결과로 0 rows affected (no-op).
--
-- 운영자 수동 SQL 0 (spring.sql.init.mode=always 설정 + jpa.defer-datasource-initialization=true).
-- 자세한 결정 근거는 ADR-022 §Decision 참조.

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
