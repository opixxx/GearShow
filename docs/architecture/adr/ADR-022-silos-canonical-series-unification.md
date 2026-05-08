# ADR-022: silos 사전 canonical 단위 — 라인 → 시리즈 통일

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: opix
- **Related**: ADR-016 (catalog search foundation), ADR-017 (crawler 한국어 매칭 정책 §D2 갱신), PR #82 (S3 미러링 PoC), PR #84 (stud_type ENUM fix)

## Context

ADR-017 §D2 의 사일로 사전 정책은 라인 단위 canonical (`Phantom GX`, `Tiempo Legend`, `Copa Pure`) 였다. PR #82 의 PoC 검증(30개 boots 적재) 에서 매칭 실패 8건 발견:

| 매칭 실패 항목 | 라인 단위 alias 와의 충돌 |
|---|---|
| Nike Phantom GT Elite DF FG | silos.yaml 에 `Phantom GT` 미등록 |
| Nike Tiempo Emerald Legend 10 Academy MG | "tiempo legend" alias 가 "tiempo emerald legend" 의 substring 아님 |
| Nike Tiempo Emerald Legend 10 Elite AG Pro | 동일 |
| Adidas X Speedportal+ FG | silos.yaml 에 `X Speedportal` 미등록 |
| Adidas X Speedportal Leyenda.1 FG | 동일 |
| Adidas x BAPE F50 Elite FG M2 | "adidas f50" alias 가 "adidas x bape f50" 의 substring 아님 |
| Mizuno Alpha Japan / Alpha Select | silos.yaml 에 `Alpha` 시리즈 미등록 |

라인 단위 사전은 **신규 라인이 등장할 때마다 운영자가 사전 갱신해야** 매칭 가능하다. 또한 Kream 의 컬러웨이/콜라보 표기("Tiempo Emerald Legend") 가 substring 매칭을 깨뜨려 운영 매칭률을 안정적으로 100% 유지하기 어렵다.

본 ADR 은 사용자 결정 — **검색 정밀도 손실(라인 단위 필터링 불가) 을 감수하더라도 운영 단순성 + 매칭률 100% 우선** — 에 따라 사일로 사전 정책을 갱신한다.

## Decision

### D1. canonical 단위 — 라인 → 시리즈

```
Before                    After
─────────────             ─────────────
Mercurial Superfly  ─┐
Mercurial Vapor     ─┴───>  Mercurial
Phantom GX          ─┐
Phantom Luna        ─┼───>  Phantom    (Phantom GT 도 자동 매칭)
Phantom GT (신규)    ─┘
Tiempo Legend       ─────>  Tiempo     (Premier 는 별개 시리즈로 유지)
Copa Pure           ─┐
Copa Mundial        ─┴───>  Copa
X Crazyfast         ─┐
X Speedportal (신규) ─┴───>  Adidas X  (D4 brand prefix 예외)
Morelia Neo         ─┐
Morelia II          ─┴───>  Morelia
(신규)               ─────>  Alpha
```

### D2. 통일 단위 정의

- **시리즈에 라인 다수**: 통합 (예: Mercurial Superfly + Vapor → `Mercurial`).
- **시리즈에 라인 단일**: 그대로 (예: Predator, Magista, Hypervenom, Rebula, Monarcida Neo, Furon, Tekela, Lethal Tigreor, DS Light, Veloce, Medusae, Tocco, Ace 16, 442, Future, Ultra, King — 통일 무관).
- **Premier (Nike Tiempo Premier)**: Tiempo 시리즈와 분리 유지. Premier 라인은 디자인·타겟·가격대 모두 Legend 와 다른 별개 라인.

### D3. canonical 명명 규칙

- canonical 에 brand prefix 안 박음 (예: `Phantom`, NOT `Nike Phantom`). brand 컬럼이 별도라 중복.
- 단 **`Adidas X` 는 예외** — `X` 단독은 collaboration 표기 ("Adidas x BAPE") 와 충돌하여 canonical 식별성 약함.

### D4. alias 정밀도 — collaboration 충돌 회피

`Adidas X` 의 alias 는 라인별 prefix(`x crazyfast`, `x speedportal`) 만 등록한다. `adidas x` 단독 alias 는 `Adidas x BAPE F50` 같은 collaboration 표기를 false positive 매칭하므로 제외. 신규 라인 등장 시 운영자가 alias 보강.

다른 시리즈는 단일 토큰 alias (`phantom`, `tiempo`, `copa`) 가 collaboration 충돌 없음 — `Nike x Off-White Phantom` 같은 협업도 의도적으로 Phantom 시리즈 매칭이 합리적.

### D5. 운영 catalog 마이그레이션

`backend/src/main/resources/schema.sql` 의 멱등 UPDATE 로 자동 backfill (`spring.sql.init.mode=always` + `defer-datasource-initialization=true` 설정 의존). 부팅 시 6개 UPDATE 실행 — 이미 통일된 행은 0 rows affected. 운영자 수동 SQL 0.

### D6. 검색 호환성

ADR-018 의 `showcase.search_text` 합성에 catalog 의 `fullNameEn`/`fullNameKo` + `brand` + `modelCode` 가 포함되므로:

| 검색어 | search_text 매칭 | siloName 매칭 |
|---|---|---|
| `?keyword=Phantom` | ✅ (fullNameEn 에 Phantom 포함) | ✅ (siloName="Phantom") |
| `?keyword=Phantom GX` | ✅ (fullNameEn 에 Phantom GX 포함) | ❌ (siloName="Phantom" 만 있음) |
| `?keyword=팬텀` | ✅ (siloNameKo + fullNameKo) | ✅ |

→ 사용자 검색 동작은 거의 동일 — 라인 단위 정밀 필터(향후 사일로 셀렉터 UI) 만 영향. 본 PR 시점에 그런 UI 없어 운영 영향 0.

## 대안 검토

### (B) 라인 분리 + alias 보강
- 매칭률은 보장되나 신규 라인 등장 시마다 사전 갱신 필요. 운영 부담 ↑.
- 컬러웨이 가공 표기 (Tiempo Emerald Legend) 모두 alias 등록해야 → alias 폭발.
- ❌ 거부

### (C) silos 사전 폐기 + raw 텍스트 저장
- 검색·필터링 깨짐. ADR-016 의 한국어 alias 컬럼 의미 상실.
- ❌ 거부

## 트레이드오프

| 영역 | 비용 | 이득 |
|---|---|---|
| 검색 정밀도 | 라인 단위 필터링 불가 (Phantom GX 단독 셀렉터 등) | 시리즈 단위 검색·집계 강화 |
| 운영 단순성 | (Adidas X 만 alias 보강 필요) | 신규 라인 자동 매칭 — 사전 갱신 부담 ↓↓ |
| 매칭률 | (collaboration false positive 1건은 D4 로 차단) | 100% 매칭 (PR #82 PoC 8건 모두 해소) |
| 데이터 reversibility | 라인 정보 영속 손실 — 향후 split 시 model_code 기반 재분류 필요 | 운영 30건 시점이라 admin UI 수동 backfill 가능 (비용 작음) |

## 후속 작업

- 사일로 셀렉터 UI 도입 시점에 라인 단위 정밀도 필요해지면 `siloLine` 컬럼 신설 검토 (`siloName`=시리즈, `siloLine`=라인).
- 다중 출처 추상화 시점에 alias false positive 케이스 일제 audit.
- clubs.yaml 에는 본 정책 적용 안 함 — 클럽은 의미상 단일 단위 (시리즈 개념 없음).
