-- ADR-016 §D1 의 결정(StudType enum 에 MG, HG 추가) 의 DB 스키마 마이그레이션 보강.
--
-- 배경: ddl-auto: update 는 ENUM 컬럼의 enum 값 변경을 자동 반영하지 않으므로
-- Java 의 StudType enum 은 7개로 갱신됐지만 DB 의 boots_spec.stud_type 은
-- 이전 5개('AG','FG','IC','SG','TF') 정의로 남아있다. Hibernate 가 'MG'/'HG' 를
-- INSERT 시 'Data truncated for column' 으로 실패 (PR #82 PoC 검증에서 발견).
--
-- 정책: schema.sql 은 ddl-auto 직후 자동 실행된다 (spring.sql.init.mode=always).
-- ALTER MODIFY COLUMN 은 멱등 — 같은 enum 정의 재적용 시 no-op (MySQL 8 동작).
-- 기존 5개 enum 값 보존 + 2개 추가 → backward compatible (기존 행 영향 0).

ALTER TABLE boots_spec MODIFY COLUMN stud_type
  ENUM('FG','SG','AG','TF','IC','MG','HG') NOT NULL;
