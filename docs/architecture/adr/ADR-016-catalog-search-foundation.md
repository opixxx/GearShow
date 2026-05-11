# ADR-016: 카탈로그 검색 보강 — StudType 확장 + 한국어 alias 컬럼 + KitType nullable

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: opix
- **Related**: ADR-014 (admin RBAC), PR #71 (catalog bulk-import API), PR #74 (Kream crawler — OPEN), 후속 ADR-017 (crawler 매칭 정책 — TBD)

> §D1 의 `StudType` MG/HG 추가 결정의 **DB 스키마 마이그레이션이 본 ADR 머지 PR 에서 누락**됨 — `ddl-auto: update` 가 ENUM 컬럼의 enum 값 변경을 자동 반영하지 않는다. PR #82 PoC 검증에서 `Data truncated for column 'stud_type'` 로 발견되어 후속 fix PR 에서 `backend/src/main/resources/schema.sql` (멱등 ALTER) + `spring.sql.init.mode=always` 설정으로 보강. 결정 자체는 유효 — 보강만 추가.

> §D3 의 `kit_type` nullable 결정도 동일 패턴으로 DB 마이그레이션 누락 — PR #82 PoC (uniform 적재) 의 INTERNAL_ERROR 1건 (Jordan x PSG 2025/26 4th kit) 으로 발견되어 후속 fix PR (`fix-extractors-and-kit-type-nullable`) 의 schema.sql 에 `ALTER TABLE uniform_spec MODIFY COLUMN kit_type ENUM('AWAY','HOME','THIRD') NULL` 추가로 보강. 동일 PR 에서 PR #85 머지 충돌로 main 에서 사라졌던 stud_type ALTER 도 schema.sql 에 복원 — 본 PR 의 schema.sql 이 마이그레이션의 single source of truth.

## Context

향후 도입할 Showcase 검색 기능(키워드 검색)을 사용자 경험 관점에서 한국어 친화적으로 만들어야 한다. 사용자는 "머큐리얼", "맨유" 같은 한국어 키워드를 자연스럽게 입력하지만, 카탈로그 데이터(Kream 크롤러 수집 대상) 의 canonical 명칭은 영문(`Mercurial Superfly`, `Manchester United`) 이다. 검색 시점에 동적 매칭을 시도하면 비용이 크고 정확도도 낮다. 따라서 **카탈로그 등록 시점에 한국어 alias 를 함께 영속**하여 향후 검색에서 단순 LIKE 매칭으로도 80%+ 매칭률을 확보하는 것이 합리적이다.

이 결정 과정에서 추가로 두 가지 데이터 모델 결함이 드러났다:

1. **StudType 부족**: 현행 `FG/SG/AG/TF/IC` 5개 enum 으로는 시장의 `Mercurial Vapor MG`, `Predator HG` 등 실제 모델을 분류할 수 없다.
2. **KitType 강제 NOT NULL**: 빈티지 유니폼(예: Manchester United 1988/90 저지) 은 시장에서 홈/어웨이/써드 분류 자체를 명시하지 않는 케이스가 존재한다.

본 ADR 은 다음 결정을 다룬다:

1. StudType enum 에 어떤 값을 추가할 것인가
2. 한국어 alias 를 어디에/어떻게 저장할 것인가
3. KitType 의 NOT NULL 제약을 어떻게 풀 것인가

**범위 명시**: crawler 의 한국어 추출/매칭 정책(keywords 메타 파싱, `silos.yaml`/`brands.yaml`/`clubs.yaml` 사전 구조)은 본 ADR 범위 밖이며, PR #74(Kream crawler) 머지 후 별도 후속 PR + ADR-017 에서 다룬다.

## 사전 발견 (조사 결과)

- **`UniformSpec.kit_type`**: 기존 `NOT NULL` (DDL + 도메인 validate 모두 강제). PR #71 bulk-import 시점에 빈티지 케이스를 막던 잠재 버그.
- **`fullName` 단일 컬럼 부재**: 검색 텍스트의 진실의 원천은 brand + modelCode + siloName/clubName 조합이지만, Kream 의 keywords 메타가 이미 한국어 + 영문 풀네임을 한 줄로 제공한다 — 이를 그대로 보관하면 매칭 텍스트로 즉시 활용 가능.
- **`ddl-auto: update`** (운영): Hibernate 가 신규 nullable 컬럼은 자동 추가하지만 **기존 NOT NULL → NULL 완화는 자동 적용되지 않는 케이스가 있음** — 운영 적용 시 수동 ALTER 필요.

## Decision

### D1. StudType enum 에 MG, HG 추가 (5개 → 7개)

| Enum | 의미 | 비고 |
|---|---|---|
| FG | Firm Ground (천연잔디) | 기존 |
| SG | Soft Ground (부드러운 천연잔디) | 기존 |
| AG | Artificial Ground (인조잔디) | 기존 |
| TF | Turf (짧은 인조잔디 — 풋살화 포함) | 기존, ADR-014 와 동일 정책 |
| IC | Indoor Court (실내 코트) | 기존 |
| **MG** | **Multi Ground (혼합 — 천연/인조 모두)** | **신규** |
| **HG** | **Hard Ground (단단한 천연잔디)** | **신규** |

**대안 검토**:
- (B) `BootsSpec.surfaceType` 자유 문자열로 흡수 후 enum 유지: 검색·집계가 enum 기준으로 이뤄질 때 일관성 깨짐. 풋살화 정책(ADR-014: `TF` 로 흡수) 과의 정합성도 약화. ❌
- (C) MG/HG 를 `surfaceType` 문자열 + StudType=AG 로 매핑: surfaceType 은 그라운드 표현이고 stud 는 신발 자체의 분류 — 의미 충돌. ❌

### D2. 한국어 alias 컬럼을 catalog 측에 직접 저장 (조회 시 매칭 X)

세 위치에 한국어 alias 컬럼을 추가한다 (모두 nullable VARCHAR):

| 테이블 | 컬럼 | 길이 | 용도 |
|---|---|---|---|
| `catalog_item` | `full_name_ko` | 255 | 한국어 풀네임 (예: "나이키 프리미어 3 FG 화이트 메탈릭 골드") |
| `catalog_item` | `full_name_en` | 255 | 영문 풀네임 (예: "Nike Premier 3 FG White Metallic Gold") |
| `boots_spec` | `silo_name_ko` | 기본(255) | 사일로 한국어 alias (예: "머큐리얼 슈퍼플라이") |
| `uniform_spec` | `club_name_ko` | 기본(255) | 클럽/국가대표 한국어 alias (예: "맨체스터 유나이티드", "대한민국") |

**근거**:
- 검색 시점 join + 사전 매칭은 비용 큼. 등록 시점에 평탄화하면 쿼리는 단순 LIKE.
- 후속 PR 에서 `showcase.search_text` 에 catalog 한국어 + 영문 풀네임을 합성한 검색 텍스트를 채우면 `?keyword=` 단일 컬럼 LIKE 로 충분 (FULLTEXT 인덱스 도입은 추후 결정).
- nullable — 사용자 직접 입력 케이스(Flutter 직접 입력 폼) 에서는 비워두고, crawler 가 채우는 케이스에서만 값이 존재.

**대안 검토**:
- (B) 별도 `catalog_item_alias(item_id, alias_text, lang)` 테이블: alias 가 다국어/다중 후보로 확장될 가능성이 있을 때만 의미 있음. 현재 시장은 한국어/영문 둘 뿐이고 풀네임은 1:1 — 테이블 추가는 과설계. ❌
- (C) 검색 시점에 사전 매칭으로 동적 매핑: 사전 갱신 시 이전 데이터의 매칭이 흔들림 + 쿼리 비용. crawler 가 등록 시점에 해석한 결과를 그대로 보관하는 편이 결정적. ❌
- (D) Read Model 분리 — Showcase 검색 도입 시점에 `catalog_search_view` 머터리얼라이즈드 뷰 또는 `showcase.search_text` 에만 합성하고, catalog 도메인은 영문 canonical 만 보유. **트레이드오프**: write-side aggregate(catalog) 와 read-side 검색 텍스트의 책임 분리 측면에서 더 깔끔하나, (1) crawler 가 한국어 alias 를 영속할 곳이 모호해지고, (2) 운영 검수 시 catalog API 응답에서 한국어 확인이 불가해 admin UI 가 별도 read view 를 알아야 함. 본 ADR 은 운영 단순성 + crawler 영속 위치 명확성을 우선하여 D 를 선택하지 않음. 다만 후속 PR-3 의 `showcase.search_text` 합성 시점에 본 결정의 트레이드오프가 실제로 어떻게 드러나는지 회고하여, 필요하면 ADR-018 등에서 read model 분리를 다시 검토한다. ❌ (현 시점)

### D3. UniformSpec.kit_type 을 nullable 화

- 도메인 `UniformSpec.validate()` 에서 `kitType` 필수 검증 제거
- JpaEntity `@Column(name = "kit_type")` 에서 `nullable = false` 제거
- DTO `CreateCatalogItemRequest.UniformSpecRequest.kitType` 의 `@NotNull` 제거
- league 도 nullable 유지 (국가대표 케이스)

**근거**:
- 빈티지 유니폼 시장(8090년대 jersey 재발매) 에 홈/어웨이/써드 분류 자체가 명시되지 않는 케이스가 존재
- 풋살 등 변형 케이스도 향후 KitType 분류가 모호할 수 있음
- 카탈로그 등록의 1차 가치는 "존재하는 아이템이라는 사실의 영속" — kitType 미확정으로 등록을 막을 이유 없음

**대안 검토**:
- (B) `KitType.UNKNOWN` enum 추가: enum 기반 통계/집계 시 UNKNOWN 비율이 noise 가 됨. 의미상 "미명시" 와 "분류 불가" 가 다른데 enum 으로 합쳐짐. ❌
- (C) kitType 제외 시 등록 거부 + 운영자 수기 보강: 빈티지처럼 시장에 분류가 존재하지 않는 경우 영원히 등록 불가. UX 손실. ❌
- (D) season 패턴 기반 vintage 판정 + non-vintage 는 도메인이 강제 — 예: season 정규식 `^(19[8-9]\d|20[0-1]\d)/.*` 매칭 시에만 nullable 허용. **트레이드오프**: "kitType=null 의 의미" 를 "빈티지 정상" 과 "추출 실패" 로 구분 가능해 운영 시그널 향상. 단 vintage 판정 규칙이 도메인에 박혀버려 시즌 표기 변형(예: "1990-91", "1990") 마다 룰 갱신 필요 + 시간이 지날수록 vintage 임계가 이동(2030년대에는 "20/21" 도 vintage 일 수 있음). 도메인 invariant 약화 비용보다 룰 유지 비용이 커 보여 미적용. 추후 운영에서 "kitType IS NULL" 의 vintage 비율이 명확히 측정되면 (D) 를 다시 검토. ❌ (현 시점)

### D4. 운영 마이그레이션 — 전체 수동 ALTER + 배포 순서 명시

운영 환경의 `ddl-auto: update` 는 신규 nullable 컬럼 추가는 보통 동작하지만 **(1) NOT NULL → NULL 완화는 자동 적용 안 됨**, **(2) 동시 배포·다중 인스턴스 기동 시 race**, **(3) 부분 적용된 ALTER 실패 시 묵묵히 진행** 등 운영 안티패턴으로 알려져 있다. 본 PR 에서는 신규 4 컬럼 + kit_type 변경을 **모두 사람이 한 번에 수동 ALTER 로 실행**하고, 그 후에 코드를 배포한다.

**배포 순서 (반드시 이 순서)**:

1. **(코드 배포 전) 사전 검증**:
   ```sql
   SELECT @@version;                              -- 8.0.27+ (INSTANT 알고리즘 가용 여부)
   SELECT count(*) FROM uniform_spec;             -- 영향 행 수 — 락 시간 추산
   SHOW CREATE TABLE uniform_spec\G               -- kit_type 의 실제 컬럼 길이 확인
   ```
2. **(코드 배포 전) 수동 ALTER — INSTANT 우선, 실패 시 INPLACE 폴백**:
   ```sql
   -- catalog_item: 한국어/영문 풀네임
   ALTER TABLE catalog_item
     ADD COLUMN full_name_ko VARCHAR(255) NULL,
     ADD COLUMN full_name_en VARCHAR(255) NULL,
     ALGORITHM=INSTANT, LOCK=NONE;

   -- boots_spec: 사일로 한국어 alias
   ALTER TABLE boots_spec
     ADD COLUMN silo_name_ko VARCHAR(255) NULL,
     ALGORITHM=INSTANT, LOCK=NONE;

   -- uniform_spec: 클럽 한국어 alias + kit_type nullable 완화
   --   주의: kit_type 의 VARCHAR 길이는 1단계 SHOW CREATE TABLE 결과를 그대로 사용 (대개 VARCHAR(255))
   ALTER TABLE uniform_spec
     ADD COLUMN club_name_ko VARCHAR(255) NULL,
     MODIFY kit_type VARCHAR(255) NULL,
     ALGORITHM=INSTANT, LOCK=NONE;
   ```
   INSTANT 가 거부되면(테이블/컬럼 정의에 따라) `ALGORITHM=INPLACE, LOCK=NONE` 으로 재시도. INPLACE 도 거부되면 LOCK=SHARED + 짧은 다운타임 윈도우로 처리.
3. **(코드 배포 전) 사후 검증**:
   ```sql
   SHOW COLUMNS FROM catalog_item;     -- full_name_ko/en Null=YES
   SHOW COLUMNS FROM boots_spec;       -- silo_name_ko Null=YES
   SHOW COLUMNS FROM uniform_spec;     -- club_name_ko Null=YES, kit_type Null=YES
   ```
4. 위 모두 OK 확인 후 코드 배포.
5. **(코드 배포 직후) Smoke**: `POST /api/admin/catalog/bulk-import` 빈티지 1건 (kitType 미지정) → 200 응답 확인 → 한국어 alias 영속 확인 (응답 DTO 에 fullNameKo 보임 — B2 반영분).

**부정합 시나리오 (사전 실패 흔적)**:
- 코드를 먼저 배포하면 빈티지 INSERT 가 NOT NULL 위반으로 실패하고, 정상 케이스 등록도 catalog_item 측 신규 컬럼이 없어 SQL 에러로 함께 다운된다. 위 1~4 순서를 어기면 운영 사고로 직결.

**장기 백로그**:
- `ddl-auto: validate` + Flyway/Liquibase 도입 — 본 ADR 범위 밖, 별도 ADR 트래킹.

## Consequences

### 긍정

- 향후 검색 기능이 단순 LIKE 만으로도 한국어 입력에 80%+ 매칭률 (crawler 데이터 + Flutter 직접 입력 모두 커버)
- StudType 표현력 확장으로 시장 모델을 분실 없이 분류 가능
- 빈티지 유니폼 등록이 가능해져 카탈로그 커버리지 확대
- 후속 PR 에서 `showcase.search_text` 합성 시 catalog 한쪽만 읽으면 됨 (사전 join 불필요)

### 비용 / 리스크

- catalog_item 테이블에 컬럼 2개(`full_name_ko/en`, 각 VARCHAR(255)) 추가 — 평균 행 크기 증가, 다만 FULLTEXT 인덱스 도입 전이라 영향 미미
- Kream 정책 변경으로 keywords 메타 형식이 바뀌면 한국어 컬럼이 비게 됨 — 하지만 도메인은 nullable 이므로 등록 자체는 동작
- crawler 에 의존한 한국어 alias 가 누적되기 시작하면 후속 정정(예: "맨체스터 유나이티드" → "맨유" 추가) 가 필요. 본 PR 에서 `CatalogItem.update()` 시그니처에 `fullNameKo/En` 을 추가하여 admin API (`PATCH /api/admin/catalog/{id}`) 로 정정 가능 (BootsSpec/UniformSpec 의 한국어 alias 정정은 후속 PR — §후속 작업 참조)
- VARCHAR(255) 결정은 PR #74 (Kream crawler) 머지 후 keywords 100건 샘플로 max length 실측을 권장. 측정 결과가 200자 초과로 나오면 후속 PR 에서 VARCHAR(500) 으로 확장 필요. FULLTEXT(n-gram) 인덱스 도입 시 utf8mb4 기준 row_format 제약(DYNAMIC=3072바이트, COMPACT=767바이트) 고려.
- 한국어 collation 은 테이블 기본 (보통 `utf8mb4_0900_ai_ci`). 후속 PR 에서 FULLTEXT 도입 시 점검.

### 후속 작업 (별도 PR)

- **ADR-017** (TBD, PR #74 머지 후): crawler keywords 파싱 + `silos.yaml`/`brands.yaml`/`clubs.yaml` 사전 + 매칭률 측정 기준 정립
- **PR-3**: `showcase.search_text` 컬럼 + Showcase 등록 시 catalog 한국어 + 영문 풀네임 합성
- **PR-4**: `?keyword=` 검색 API + Cucumber + (조건부) MySQL FULLTEXT(n-gram) 인덱스
  - **FULLTEXT 도입 기준**: catalog_item 행 수가 N=10,000 미만일 때는 LIKE 풀스캔 허용 (cold path), 10,000 이상이면 FULLTEXT 도입 검토. 도입 시 `WITH PARSER ngram` + `ngram_token_size=2` 권장. 검색 컬럼은 `catalog_item.full_name_ko/en` 보다는 `showcase.search_text` 단일 합성 컬럼이 합리적 (write-side 와 read-side 책임 분리).
  - **인덱스 비용**: 본 PR 시점에는 `full_name_ko/en` 에 인덱스 추가하지 않음 — 검색 API 가 없는 상태에서 인덱스를 만들면 데드 인덱스가 되어 INSERT 비용만 증가.
- **alias 정정 API (BootsSpec/UniformSpec)**: 본 PR 의 `CatalogItem.update()` 는 `fullNameKo/En` 만 갱신. `BootsSpec.siloNameKo` / `UniformSpec.clubNameKo` 정정은 후속 PR — Spec 레벨 update API 가 필요해질 때 함께 도입.
- **별도 Flutter PR**: 직접 입력 폼 단순화 (B-필수 — 카테고리 + 브랜드 + title + description), 한국어 alias 는 사용자가 직접 입력 안 함

### 운영 적용 체크리스트

D4 의 **배포 순서 1~5** 를 그대로 따름. 요약 체크리스트:

- [ ] `SELECT @@version;` — MySQL 8.0.27+ 확인 (INSTANT 가용)
- [ ] `SELECT count(*) FROM uniform_spec;` — 영향 행 수 확인
- [ ] `SHOW CREATE TABLE uniform_spec\G` — kit_type 의 실제 컬럼 길이 확인 (D4 ALTER 의 `VARCHAR(...)` 값에 그대로 사용)
- [ ] D4 의 ALTER 3 문 실행 (catalog_item / boots_spec / uniform_spec) — INSTANT 우선, 폴백 INPLACE
- [ ] `SHOW COLUMNS FROM catalog_item;` — `full_name_ko`, `full_name_en` `Null=YES`
- [ ] `SHOW COLUMNS FROM boots_spec;` — `silo_name_ko` `Null=YES`
- [ ] `SHOW COLUMNS FROM uniform_spec;` — `club_name_ko` `Null=YES`, `kit_type` `Null=YES`
- [ ] 코드 배포
- [ ] 코드 배포 직후 smoke: 빈티지 1건 bulk-import → 200 응답 + `getCatalogItem` 응답에 fullNameKo 보임

### 롤백

**시나리오 A — 한국어 alias 만 비우기 (코드는 유지, 데이터 청소)**:
```sql
UPDATE catalog_item SET full_name_ko = NULL, full_name_en = NULL;
UPDATE boots_spec   SET silo_name_ko = NULL;
UPDATE uniform_spec SET club_name_ko = NULL;
```

**시나리오 B — 컬럼 자체 제거 (코드 revert 후)**:
코드를 revert 한 뒤(JpaEntity 에 컬럼이 더 이상 정의되지 않음), 운영 DB 에서 컬럼 제거:
```sql
ALTER TABLE catalog_item DROP COLUMN full_name_ko, DROP COLUMN full_name_en;
ALTER TABLE boots_spec   DROP COLUMN silo_name_ko;
ALTER TABLE uniform_spec DROP COLUMN club_name_ko;
```
주의: 코드 revert 전에 컬럼만 DROP 하면 다음 부팅 시 ddl-auto 가 컬럼을 다시 생성할 수 있다 (current entity 정의를 따라감). 코드 revert → 부팅 → 컬럼 DROP 순서.

**시나리오 C — `kit_type` NOT NULL 복구 가능성 점검**:
```sql
SELECT count(*) FROM uniform_spec WHERE kit_type IS NULL;
-- 결과 = 0 이어야 NOT NULL 복구 가능 (빈티지 등록 1건이라도 있으면 복구 불가)
```
단방향성: 한 번 nullable 화하고 빈티지 INSERT 1건이 들어오면 NOT NULL 복구는 사실상 불가. 본 ADR 은 그 단방향성을 수용한다. 운영에서 `kit_type IS NULL` 비율을 모니터링 메트릭으로 추가하여, 추출 실패 (의도되지 않은 null) 가 비정상 증가하면 알람으로 잡는다.
