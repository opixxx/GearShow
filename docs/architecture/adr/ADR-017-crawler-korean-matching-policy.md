# ADR-017: Crawler 한국어 매칭 정책 — 사전 + keywords 파싱 + 매칭률 기준

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: opix
- **Related**: ADR-016 (catalog search foundation), PR #74 (Kream crawler), PR #75 (catalog search-foundation backend), ADR-021 (catalog image mirroring), ADR-022 (silos 시리즈 통일)

> 본 ADR 작성 시점의 도구 이름은 `kream-crawler` 였으나, ADR-021 결정에 따라 `catalog-crawler` 로 rename 됨. 외부 사이트 식별자(`KreamClient` 등) 와 운영 정책(약관 명시) 는 의도적으로 유지.

> §D2 의 사일로 사전 단위(라인 단위 canonical) 결정은 ADR-022 (PR-Q3) 에서 시리즈 단위로 갱신됨. 결정 변경 사유는 ADR-022 §Context (PR #82 PoC 매칭 실패 8건) 참조.

> §D2 의 silos/brands/clubs 사전은 출처 무관 — Kream 및 crazy11 (ADR-023) 모두 동일 사전 사용. 신규 출처 추가 시 사전 재사용 가능.

> **Kream 검색 페이지네이션** (후속 PR `kream-search-pagination`): §D1 검색 endpoint 기반 discover 가 첫 페이지(50건) 만 fetch 하던 한계를 `?page=<N>` 순회로 해소. 종료 조건 3개 — limit 도달 / 페이지 새 ID 0건 / `_MAX_PAGES=20` 가드. 1 keyword 로 최대 ~1000건 추출 가능 (UNIFORM 50건 → 수백건 효과 기대).

## Context

ADR-016 이 catalog 도메인에 한국어 alias 컬럼 (`fullNameKo/En`, `siloNameKo`, `clubNameKo`) 과 StudType `MG/HG`, `kitType` nullable 화를 도입했다. 이 결정은 **crawler 가 Kream 페이지에서 한국어 데이터를 추출해 채워주는 것**을 전제로 한다. PR #74 는 crawler 의 boots 흐름을 main 에 들여놨지만 다음 사항이 미정인 채로 머지됐다:

1. 한국어 풀네임을 Kream HTML 의 어디서 추출하는가
2. 사일로/브랜드/클럽 사전을 어떤 구조로 두는가
3. 매칭 알고리즘은 무엇인가 (substring 충돌, 영/한 우선순위)
4. 운영 매칭률의 하한선은 얼마인가
5. `kitType` 추출 실패 시 정책 (ADR-016 의 nullable 결정과 정합)

본 ADR 은 위 다섯 결정을 정립한다.

## Decision

### D1. keywords 메타 파싱이 한국어 풀네임의 1차 소스

Kream 상품 페이지의 `<meta name="keywords">` 는 콤마 구분 3-token (modelCode / 한국어 풀네임 / 영문 풀네임) 형식을 안정적으로 제공한다 (실측):

```
"AT5889-174,나이키 프리미어 3 FG 화이트 메탈릭 골드,Nike Premier 3 FG White Metallic Gold"
```

`product_parser.parse_keywords()` 가 이 형식만 정상으로 처리하고, **1-token / 0-token 입력은 모두 (None, None, None) 반환** — 1-token 만 modelCode 로 추정하는 것은 오추론보다 누락이 안전하다 (운영 검수에서 사일로 매칭 실패로 잡힌다).

`style_code` 의 추출 우선순위는: `keywords[0]` → JSON-LD `sku` → 본문 `<dt>스타일 코드</dt>` 라벨. keywords 가 가장 권위 있는 소스이고 JSON-LD/라벨은 fallback.

**대안 검토**:
- (B) JSON-LD `Product.name` 만 사용: 영문만 노출되어 한국어 풀네임 부재. 검색 보강 가치 0. ❌
- (C) `og:description` 의 한국어 사용: og:description 형식이 페이지마다 일관되지 않음 (실측). keywords 가 더 안정적. ❌

### D2. 사전 구조 — silos.yaml + brands.yaml + clubs.yaml

세 yaml 파일을 `kream_crawler/dictionaries/` 에 두고 `importlib.resources` 로 로드 (PR #74 패턴 유지).

```yaml
# silos.yaml — 사일로 + brand
- canonical: "Mercurial Superfly"
  brand: "Nike"
  aliases: ["mercurial superfly", "머큐리얼 슈퍼플라이"]

# brands.yaml — 브랜드 + 한국어 alias (8개)
- canonical: "Nike"
  aliases: ["나이키"]

# clubs.yaml — 클럽 + 국가대표 + league (32개, 11개 국가대표 포함)
- canonical: "Manchester United"
  aliases: ["맨체스터 유나이티드", "맨유"]
  league: "EPL"
- canonical: "Korea"
  aliases: ["대한민국", "한국"]
  league: null         # 국가대표 — ADR-016 §D3 league nullable
```

각 entry 의 `aliases` 는 영문 lowercase 와 한국어를 함께 가진다. **alias 중 한글 문자가 포함된 첫 항목** 이 `siloNameKo` / `clubNameKo` 로 채워진다 (`_extract_korean_alias()`).

**대안 검토**:
- (B) 단일 yaml 파일에 silos/brands/clubs 통합: 책임이 섞여 운영자 갱신 시 conflict 위험. 카테고리별 파일 분리가 read-side 단순함. ❌
- (C) 외부 DB 또는 원격 사전: 운영 단순성 상실 — yaml 파일 직접 편집 워크플로가 가장 빠르다. ❌
- (D) 백엔드에서 사전 노출 (예: `GET /api/admin/catalog/dictionaries`): 백엔드 ↔ crawler 의존 역전. crawler 는 백엔드 contract 형식만 알면 충분. ❌

### D3. 매칭 알고리즘 — Longest Match Wins + Canonical Tiebreaker

```python
def match_X(haystack: str, dictionary: list[X]) -> X | None:
    matches = [(len(alias), x) for x in dictionary for alias in x.aliases if alias in haystack]
    matches.sort(key=lambda x: (-x[0], x[1].canonical))   # 길이 desc, canonical asc
    return matches[0][1] if matches else None
```

**핵심 결정**:
1. **Longest match wins**: `Mercurial` 과 `Mercurial Vapor` 가 모두 사전에 있을 때 `Mercurial Vapor` 가 이긴다.
2. **Canonical 알파벳 tiebreaker**: 동률 길이 alias 시 결정성 보장 (yaml 순서에 의존하지 않음).
3. **영문 우선, 한국어 fallback**: `match_brand` 는 `brand_en` 의 canonical 매칭 우선, 실패 시 `name_ko` 의 한국어 alias 매칭.
4. **`match_club` 은 영/한 양쪽 검색**: 클럽명은 영문/한국어 둘 중 하나만 노출되는 케이스 흔해 양쪽 검색.

**대안 검토**:
- (B) 정확 일치 (substring 매칭 X): 페이지마다 부수 텍스트가 섞여 있어 정확 일치는 매칭률 급락. ❌
- (C) 정규식 word boundary: 한국어에는 word boundary 가 없음 (공백·구두점 외 의미 없음). ❌

### D4. 매칭률 기준 — 하한선 + 미달 시 액션

운영 30건 기준 매칭률 하한:

| 지표 | 하한 | 미달 시 액션 |
|---|---|---|
| `silo_matched / total` (BOOTS) | ≥ 70% | `silos.yaml` 에 미매칭 사일로 추가 → 재크롤링 |
| `club_matched / total` (UNIFORM) | ≥ 70% | `clubs.yaml` 에 미매칭 클럽 추가 → 재크롤링 |
| `season_extracted / total` (UNIFORM) | ≥ 80% | 시즌 정규식 보강 (예: `2024시즌` 같은 변형) |
| `brand_matched / total` | ≥ 95% | `brands.yaml` 에 미매칭 브랜드 추가 (드물게 발생) |
| `korean_full_name_present / total` | ≥ 95% | Kream keywords 형식 변경 의심 — `parse_keywords()` 로직 재검토 |

`compute_match_stats()` 가 위 카운트를 모두 계산. cli 가 export 후 통계를 stderr 로 출력하므로 운영자는 30초 내 매칭률을 확인 가능.

**`kit_type_inferred / total`** 에는 명시적 하한선을 두지 않는다 — 빈티지 비율이 데이터셋마다 다르고, 빈티지(정상 None)와 추출 실패(비정상 None)를 카운트만으로 구분할 수 없기 때문. ADR-016 §D3 와 동일한 nullable 정책 유지.

**대안 검토**:
- (B) 매칭률 하한 미달 시 export 차단: 부분 결과라도 운영 가치가 있어 차단 X. 경고만. ❌
- (C) 매칭률을 백엔드로 자동 보고: crawler ↔ 백엔드 결합 증가. 본 ADR 은 운영자 수동 검수 모델 유지. ❌

### D5. kitType 추출 실패 — None 반환 (ADR-016 §D3 정합)

영문 → 한국어 fallback 으로 추출 시도. 둘 다 실패하면 `None` 반환. ADR-016 §D3 와 동일하게 빈티지(정상 None)와 추출 실패(비정상 None)를 도메인에서 구분하지 않는다. 운영 모니터링에서 `kit_type IS NULL AND season IS NOT NULL AND season < '20'` 같은 패턴으로 빈티지 비율을 추정한다.

**한국어 매핑** (`_KIT_TYPE_KO_MAP`):
```python
{"홈": "HOME", "어웨이": "AWAY", "원정": "AWAY", "써드": "THIRD", "서드": "THIRD"}
```

`THIRD` / `3RD` 는 모두 `THIRD` 로 정규화. 정책 변경 시 본 ADR + 단위 테스트 동기화 필요.

## Consequences

### 긍정

- catalog 의 한국어 alias 컬럼이 PR #75 ADR-016 의도대로 채워짐 → 후속 PR-3 (showcase.search_text) 의 합성 소스가 안정적
- 사전 갱신 = yaml 직접 편집 + 재크롤링이라 운영 워크플로 단순
- 매칭 결정성 (canonical tiebreaker) 으로 회귀 디버깅 용이
- cli 가 normalizer 의 dict 키 형태에 결합되지 않음 (`compute_match_stats` 위임)

### 비용 / 리스크

- Kream `<meta name="keywords">` 형식 변경 시 한국어 풀네임 일괄 누락 — `korean_full_name_present` 메트릭이 95% 미만으로 떨어지면 즉시 신호
- 사전이 크롤러 패키지에 박혀 있어 사전 갱신마다 재배포 필요. 외부화는 운영 복잡도 증가로 미적용 (D2 §대안 C)
- club tiebreaker (canonical 알파벳) 가 yaml 순서와 다른 결과를 줄 수 있어 사전 갱신 시 단위 테스트로 회귀 보호 필수

### 후속 작업

- ~~**PR-B (crawler 안정성)**: defusedxml / 부분 결과 보존 (partial.json) / `except CrawlerBlockedError` re-raise~~ — **PR-B 머지 완료** (kream-crawler-hardening). MatchStats NamedTuple 추가 + `_extract_korean_alias` 한글 범위/순서 의존 보강 + silos/brands/clubs 70 entry parametrize 회귀 + AG/IC StudType 케이스 함께.
- ~~**PR-3 (showcase.search_text 합성)**~~ — PR #77 머지 완료
- **사전 보강** (운영 사이클): 운영 후 매칭률 보고에 따라 `silos.yaml` / `brands.yaml` / `clubs.yaml` 점진 보강. parametrize 회귀가 yaml 변경을 자동 검증하므로 안전.
- **TypedDict ↔ Java DTO 자동 회귀 검증** (PR #76 architecture-reviewer Major + code-reviewer S1): backend 빌드 시 `BulkImportCatalogItemRequest` 의 JSON Schema 를 `tools/kream-crawler/contracts/bulk-import-item.schema.json` 으로 export → crawler 측 `jsonschema` 로 export 직전 검증. 양방향 보호 (백엔드 DTO 변경 누락 시 backend CI fail). 별도 PR + ADR — 본 PR 은 docstring 추적 + parametrize 회귀로 충당.
- **`KreamClient` SRP 분리** (PR #74 code-reviewer Major): RateLimiter / PathPolicy / BlockedStatusGuard 분리. 단일 스레드 도구라 즉시 위협 없음 — 멀티스레드 또는 async 도입 시 리팩토링.
- **PR-4 (`?keyword=` 검색 API)**: ADR-018 위에서 진행. crawler 와 무관.

## 참조 매트릭스 (백엔드 contract)

본 ADR 의 TypedDict 가 거울로 삼는 백엔드 클래스:

| Python TypedDict | Java DTO/도메인 | 추가 시점 |
|---|---|---|
| `CatalogItem.fullNameKo/En` | `CatalogItem.fullNameKo/En` | PR #75 (ADR-016) |
| `BootsSpecItem.siloNameKo` | `BootsSpec.siloNameKo` | PR #75 (ADR-016) |
| `UniformSpecItem.clubNameKo` | `UniformSpec.clubNameKo` | PR #75 (ADR-016) |
| `UniformSpecItem.kitType: nullable` | `UniformSpec.kitType: nullable` | PR #75 (ADR-016 §D3) |
| `BootsSpecItem.studType` MG/HG | `StudType.MG / HG` | PR #75 (ADR-016 §D1) |

백엔드 DTO 변경 시 `kream_crawler/normalizer.py` 의 TypedDict 정의 + 본 ADR 의 매트릭스 동기화 필수.
