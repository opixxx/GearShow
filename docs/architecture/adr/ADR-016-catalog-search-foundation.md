# ADR-016: 카탈로그 검색 보강 — StudType 확장 + 한국어 alias 컬럼 + KitType nullable

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: opix
- **Related**: ADR-014 (admin RBAC), PR #71 (catalog bulk-import API), PR #74 (Kream crawler — OPEN), 후속 ADR-017 (crawler 매칭 정책 — TBD)

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

### D4. 운영 마이그레이션 — 수동 ALTER 한 줄

```sql
ALTER TABLE uniform_spec MODIFY kit_type VARCHAR(20) NULL;
```

- 신규 컬럼(`full_name_ko/en`, `silo_name_ko`, `club_name_ko`) 은 ddl-auto: update 가 자동 추가
- `kit_type` NOT NULL → NULL 완화는 Hibernate 가 자동 적용 안 할 가능성 → 운영 배포 직전 수동 실행
- 변경 후 `SHOW COLUMNS FROM uniform_spec;` 로 `Null=YES` 확인

## Consequences

### 긍정

- 향후 검색 기능이 단순 LIKE 만으로도 한국어 입력에 80%+ 매칭률 (crawler 데이터 + Flutter 직접 입력 모두 커버)
- StudType 표현력 확장으로 시장 모델을 분실 없이 분류 가능
- 빈티지 유니폼 등록이 가능해져 카탈로그 커버리지 확대
- 후속 PR 에서 `showcase.search_text` 합성 시 catalog 한쪽만 읽으면 됨 (사전 join 불필요)

### 비용 / 리스크

- catalog_item 테이블에 컬럼 2개(`full_name_ko/en`, 각 VARCHAR(255)) 추가 — 평균 행 크기 증가, 다만 FULLTEXT 인덱스 도입 전이라 영향 미미
- Kream 정책 변경으로 keywords 메타 형식이 바뀌면 한국어 컬럼이 비게 됨 — 하지만 도메인은 nullable 이므로 등록 자체는 동작
- crawler 에 의존한 한국어 alias 가 누적되기 시작하면 후속 정정(예: "맨체스터 유나이티드" → "맨유" 추가) 시 backfill 필요 — 본 ADR 범위는 1차 영속까지

### 후속 작업 (별도 PR)

- **ADR-017** (TBD, PR #74 머지 후): crawler keywords 파싱 + `silos.yaml`/`brands.yaml`/`clubs.yaml` 사전 + 매칭률 측정 기준 정립
- **PR-3**: `showcase.search_text` 컬럼 + Showcase 등록 시 catalog 한국어 + 영문 풀네임 합성
- **PR-4**: `?keyword=` 검색 API + Cucumber + (필요 시) MySQL FULLTEXT(n-gram) 인덱스
- **별도 Flutter PR**: 직접 입력 폼 단순화 (B-필수 — 카테고리 + 브랜드 + title + description), 한국어 alias 는 사용자가 직접 입력 안 함

### 운영 적용 체크리스트

- [ ] `ALTER TABLE uniform_spec MODIFY kit_type VARCHAR(20) NULL;` 실행
- [ ] `SHOW COLUMNS FROM catalog_item;` — `full_name_ko`, `full_name_en` 자동 생성 확인
- [ ] `SHOW COLUMNS FROM boots_spec;` — `silo_name_ko` 자동 생성 확인
- [ ] `SHOW COLUMNS FROM uniform_spec;` — `club_name_ko` 자동 생성, `kit_type` Null=YES 확인

### 롤백

- catalog 데이터에 한국어 alias 가 일부 채워진 상태에서 롤백할 경우, 도메인은 nullable 이므로 코드 revert 만으로 충분 (컬럼 자체는 운영 DB 에 남아도 무해)
- `kit_type` 다시 NOT NULL 복구는 데이터 검증 필요: `SELECT count(*) FROM uniform_spec WHERE kit_type IS NULL` 이 0 이어야 안전. 빈티지 데이터가 이미 들어가 있으면 복구 불가 — 사실상 이 ADR 은 단방향(한 번 nullable 화하면 되돌리지 않음).
