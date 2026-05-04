# ADR-019: Showcase 검색 키워드 정책 — `?keyword=` API + 정규화 + 임계

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: opix
- **Related**: ADR-016 (catalog search foundation), ADR-017 (crawler 한국어 매칭), ADR-018 (showcase.search_text 합성), PR #75/#76/#77/#78

## Context

ADR-018 이 `showcase.search_text` 컬럼에 catalog 한국어 alias + 직접 입력값을 합성하여 영속하는 기반을 만들었다. PR-4 는 그 위에 사용자 검색 진입점인 `GET /api/v1/showcases?keyword=...` API 를 추가한다. 본 ADR 은 다음 결정을 다룬다:

1. 검색 정규화 정책 — 대소문자/공백/자모 분리 등
2. FULLTEXT 도입 임계 + 모니터링 메커니즘
3. keyword 길이 제한 + Bean Validation
4. 운영 시점 backfill prerequisite
5. 본 PR 미적용 정규화 (자모 분리 등) 의 후속 결정 시점

## 사전 발견 (조사 결과)

- **N (showcase 행 수)**: 운영 시점 < 10,000 가정 — LIKE 풀스캔 허용 임계.
- **utf8mb4 collation**: MySQL 8 default `utf8mb4_0900_ai_ci` (accent-insensitive, case-insensitive). 그러나 한국어 자모 분리 / 공백 정규화는 collation 만으로 부족.
- **ADR-018 §D5 backfill**: 코드 배포 후 기등록 Showcase 의 `search_text` 가 NULL — 운영자가 backfill 안 하면 PR-4 검색 결과에서 영구 제외.
- **`@Size(min, max)` Bean Validation**: keyword 길이 제한이 없으면 클라이언트가 1MB 같은 거대 입력으로 LIKE 비용 폭증 + DoS 표면.

## Decision

### D1. 검색 정규화 — collation case-insensitive + LIKE escape + Service 단 trim

```sql
WHERE search_text LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
```

**적용 정규화**:
- **대소문자 무시**: `utf8mb4_0900_ai_ci` collation 의 자동 case-insensitive 동작에 의존. 양측 `LOWER()` 호출 제거 — 함수 호출 cost (VARCHAR(1000) × N 행 string lowercase 변환) 제거. 한글은 collation 영향 없음.
- **LIKE 메타문자 escape**: `%` / `_` / `\\` 를 리터럴로 처리. `ShowcasePersistenceAdapter.escapeLike()` 가 입력을 변환하고 JPQL 의 `ESCAPE '\\'` 가 backslash escape 인식. 사용자가 `?keyword=%` 입력 시 amplification (모든 행 매칭) 차단.
- **좌우 공백 trim**: `ListShowcasesService.list` 가 keyword 좌우 공백 제거. 빈 문자열 (`?keyword=`) 은 Controller `@Size(min=1)` 에서 거부, 공백 문자열 (`?keyword=   `) 은 Service trim 후 빈 → 전체 ACTIVE 목록 fallback (controller-service 비대칭 차단).

**미적용 (본 PR)**:
- 다중 공백 단일화 (`"머   큐리얼"` → `"머 큐리얼"`)
- 자모 분리 (한글 IME 중간 입력 검색)
- 검색어 토큰화 (다중 키워드 AND/OR)
- 동의어 / 형태소 분석

**대안 검토**:
- (B) 명시적 `LOWER()` 양측 호출 (이전 정책) — collation 변경 시 안전이라는 근거였으나 함수 호출 cost (database-optimizer Critical #1) 가 latency 30~60% 영향 추정. **collation 의존 + 운영 체크리스트에서 collation 검증 단계 추가** 가 더 합리. ❌ (이전 채택, 현 정책 변경)
- (C) 공백/자모 정규화 추가 — `search_text` 합성 시점 (ADR-018 §D3) 과 양측 일관 필요. backfill 비용. ADR-020 후속.
- (D) Elasticsearch — N=10,000 미만 가정에서 과대 비용. ❌

### D2. FULLTEXT 도입 임계 — 행 수 N≥10,000 + p99 SLO + transition window

**cost model 근거** (database-optimizer Critical #2):
- VARCHAR(1000) 평균 200~600자 (ADR-018 §D3) × 10,000행 = 2~6MB scan size
- InnoDB buffer pool default ≥128MB 에서 in-memory 풀스캔
- `(showcase_status, created_at, id)` 인덱스 존재 시 옵티마이저는 인덱스 driven scan + LIKE filter + LIMIT early termination — 매칭률이 높은 키워드일수록 LIMIT 으로 조기 종료. 매칭률 매우 낮은 키워드 (예: 1/10000) 가 worst case.
- 본 정책의 N=10,000 은 worst case (low-selectivity) 기준 보수적 임계

**SLO target**:
- `GET /api/v1/showcases?keyword=` p99 < 500ms, p95 < 200ms
- LIKE 풀스캔 latency 가 target 초과 시 즉시 알람 — count 임계 도달 전이라도 SLO 위반은 자체로 트리거

**N < 10,000 (현 시점)**:
- LIKE 풀스캔 (또는 인덱스 driven scan + filter) 허용
- 인덱스 추가 없음 (ADR-018 §D5 와 정합)
- 운영 검증: `EXPLAIN ANALYZE SELECT * FROM showcase WHERE search_text LIKE '%머큐리얼%' ORDER BY created_at DESC LIMIT 21` — `Using index condition` 또는 `Using filesort` 여부 확인. filesort 발생 시 인덱스 추가 우선순위 격상.

**Transition window 가드** (N≥10,000 알람 시):
- N≥10,000 도달 알람 → ADR-020 또는 ADR-019 v1.1 PR 시작 신호
- N≥15,000 도달 시 keyword API rate limiter 강제 (per-user 1 req/sec) — 마이그레이션 PR 머지 전 임시 차단
- 마이그레이션 SLA: 알람 후 7일 이내 머지. 초과 시 keyword API 503 fallback (degraded mode) — list 자체는 정상 동작
- p99 SLO 위반이 N 임계 전에 발생하면 (예: low-selectivity 키워드 폭주) 즉시 ADR-020 우선 트리거

**N ≥ 10,000 도달 시 (후속 ADR-020 또는 ADR-019 v1.1)**:
```sql
ALTER TABLE showcase
  ADD FULLTEXT INDEX ft_search_text (search_text)
  WITH PARSER ngram;
SET GLOBAL ngram_token_size = 2;   -- 한글 2자 매칭

-- 쿼리 변경:
SELECT * FROM showcase
WHERE MATCH(search_text) AGAINST(:keyword IN BOOLEAN MODE);
```

**모니터링 메커니즘**:
- Prometheus actuator metric: `showcase_count` (또는 `mysql_global_status_innodb_rows_read{table="showcase"}`)
- 알람: `showcase_count >= 10000` 도달 시 Slack/email.
- LIKE 쿼리 latency p99 측정 — `actuator/metrics/http.server.requests{uri="/api/v1/showcases",method="GET"}`.

### D3. keyword 길이 제한 — `@Size(min=1, max=100)`

```java
@RequestParam(required = false)
@Size(min = 1, max = 100, message = "검색어는 1~100자여야 합니다")
String keyword
```

**근거**:
- `min=1`: 빈 문자열 (`?keyword=`) 거부 — 의미 없는 풀스캔 요청 차단
- `max=100`: 거대 입력 LIKE 비용 폭증 차단 + DoS 표면 축소. 운영 사용자 검색어는 평균 5~20자.
- `required=false`: 미제공 (`?keyword` 없음) 은 기존 list 동작 (전체 ACTIVE 목록)

**대안 검토**:
- (B) `min=2`: 한 글자 검색 거부. 운영자 admin 검색에서 한 글자 (예: `?keyword=A`) 가 유효 케이스라 `min=1` 유지. ❌
- (C) `max=50`: 더 보수적. 그러나 사용자가 긴 키워드 (예: 풀네임) 입력 가능성 고려. `max=100` 이 균형. ❌

### D4. 운영 backfill — PR-4 prerequisite

ADR-018 §D5 의 backfill SQL 1회 실행이 PR-4 머지 직전 prerequisite. 미실행 시 기등록 Showcase 가 `search_text=NULL` 로 검색 결과에서 영구 제외.

**운영 절차**:
1. PR-4 머지 직전: ADR-018 §D5 의 catalog 연결 + 직접 입력 backfill SQL 실행
2. `SELECT count(*) FROM showcase WHERE search_text IS NULL` = 0 확인
3. PR-4 코드 배포
4. smoke: `?keyword=머큐리얼` 같은 검색이 catalog 연결 + 직접 입력 모두 매칭

backfill 미실행 검증을 위해 본 PR 의 `findByKeywordFirstPage` / `findByKeywordWithCursor` 가 `search_text IS NOT NULL` 가드 — backfill 안 한 행이 silent 하게 결과에서 빠지는 게 의도. NULL 행이 포함되면 `LOWER(NULL) LIKE ...` 로 FALSE 반환되어 결과 영향 없으나, `IS NOT NULL` 명시가 의도 표현 + 일부 DB 엔진에서 인덱스 활용 향상.

### D5. 미적용 정규화의 후속 결정 시점

본 PR 미적용 항목 (D1 의 (C)) 도입 시점:

| 항목 | 도입 시점 / 트리거 |
|---|---|
| 공백 정규화 (다중 공백 단일화) | 운영 후 사용자 검색 실패 사례 분석. 후속 ADR-020. |
| 자모 분리 (한글 IME 중간 입력 — "머쿠ㅇ" → "머큐리얼") | Flutter 폼 단순화 후 IME 중간 입력 비율 측정 (Search analytics). |
| 다중 키워드 AND/OR (`?keyword=머큐리얼+엘리트`) | 사용자 검색 패턴 분석 — 단일 키워드 80%+ 라면 미도입. |
| 동의어 (`머큐리얼` ↔ `Mercurial`) | catalog 의 한국어/영문 풀네임이 이미 합성되어 있어 부분 해소. 동의어 사전 도입은 운영 후 결정. |
| 형태소 분석 (한국어 stemming) | N≥100,000 도달 + Elasticsearch 도입 시점. 본 ADR 범위 밖. |

## Consequences

### 긍정

- 단순 LIKE + LOWER 만으로 한국어/영문 + 직접 입력값 모두 매칭 — ADR-018 의 `search_text` 합성 효과
- 후속 ADR-020 의 정규화 v2 / FULLTEXT 도입 시 search_text 합성 정책 (ADR-018 §D3) 도 함께 갱신해야 하는 점이 명시됨 (양측 일관성)
- N=10,000 임계 모니터링으로 LIKE → FULLTEXT 전환 시점 자동 감지

### 비용 / 리스크

- 공백/자모 정규화 미적용으로 사용자 검색 실패율 일정 비율 발생 (예: "머 큐리얼" 다중 공백) — 후속 분석으로 결정
- backfill prerequisite 미실행 시 PR-4 출시 후 기등록 검색 누락 — 운영 체크리스트로 차단
- LIKE 풀스캔이 N≥10,000 시 latency 폭증 — 알람 모니터링으로 사전 감지
- `@Size(max=100)` 우회는 `?keyword=` 100회 반복 같은 amplification 가능 — Rate limiter 별도 적용 필요 (본 ADR 범위 밖)

### 후속 작업 (별도 PR)

- **ADR-020 (TBD)**: 검색 정규화 v2 — 공백 단일화 + 자모 분리 + 다중 키워드. 운영 후 사용자 검색 실패 사례 기반 결정.
- **FULLTEXT 도입 PR**: N≥10,000 도달 시. `WITH PARSER ngram` + `ngram_token_size=2`. backfill 또는 search_text 재합성 정책 동기화.
- **Search analytics**: 인기 키워드 / 매칭 실패율 / latency p99 대시보드.
- **Rate limiter**: keyword API 의 amplification 방어.
- **내 쇼케이스 검색** (`MyShowcaseController` 의 `?keyword=` 추가): 본 PR 은 공개 목록만. 후속 PR.

### 운영 적용 체크리스트

> **순서 엄수**: ADR-018 §D5 의 backfill 이 PR-4 prerequisite. 미실행 시 검색 결과 누락.

- [ ] **(PR-4 머지 직전)** ADR-018 §D5 backfill 1회 실행 + `pending=0` 확인
- [ ] PR-4 코드 배포
- [ ] smoke: `GET /api/v1/showcases?keyword=머큐리얼` (또는 사용자 데이터셋의 알려진 키워드) 매칭 확인
- [ ] 운영 모니터링 추가 — `showcase_count` metric + 임계 N=10,000 알람
- [ ] LIKE 쿼리 latency p99 측정 시작

### 롤백

- `?keyword=` 파라미터 제거 (Controller revert) → 기존 list 동작
- search_text 컬럼은 그대로 유지 (PR #77 영역 무관)
- 기등록 backfill 데이터는 그대로 — 다음 검색 PR 재도입 시 재사용
