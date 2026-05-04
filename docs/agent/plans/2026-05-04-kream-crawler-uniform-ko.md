# EXEC_PLAN: kream-crawler-uniform-ko

- **Type**: feature
- **Status**: in_progress
- **Risk**: Caution (외부 사이트 매칭률 의존 + 백엔드 contract 동기화 + 사전 신규)
- **Created**: 2026-05-04
- **Branch**: feature/kream-crawler-uniform-ko
- **Worktree**: /Users/opix/gearshow-kream-crawler-uniform-ko
- **Port**: 9000
- **Base**: main 의 PR #74 (kream-crawler) + PR #75 (catalog-search-foundation) 머지 후 — 두 의존 모두 해결됨

---

## 1. 목표

PR #75 (catalog-search-foundation) 가 catalog 도메인에 추가한 한국어 alias 컬럼 (`fullNameKo/En`, `siloNameKo`, `clubNameKo`) + StudType `MG/HG` + `kitType` nullable 화의 **Python crawler 측 채우기**를 구현한다. PR #74 의 EXEC_PLAN Step 6~8 (옵션 1로 분리됐던 것) 을 본 PR 로 처리하며, ADR-017 로 매칭 정책을 정립한다.

핵심 결과물: Kream 의 boots/uniform 두 카테고리 모두 한국어 풀네임 + 한국어 alias 가 채워진 `BulkImportCatalogItemRequest` JSON 으로 export 가능한 상태.

## 2. 범위

### In
- **crawler 한국어 추출** — `<meta name="keywords">` 의 콤마 구분 토큰 (modelCode, 한국어 풀네임, 영문 풀네임) 파싱
- **사전 신규** — `brands.yaml` (8개), `clubs.yaml` (30~40개 club + 국가대표)
- **사전 보강** — `silos.yaml` 의 한국어 alias 보강 + `STUD_PATTERN` 에 `HG` 추가
- **uniform 지원** — `--category uniform` 활성화 (기존 NotImplementedError 해제), `normalizer.to_uniform_item()` 신규, 시즌 정규식 (`24/25`, `1988/90`), `kitType` 영/한 추출 (HOME/AWAY/THIRD ↔ 홈/어웨이/써드)
- **백엔드 contract 동기화** — `CatalogItem` TypedDict 에 `fullNameKo/En` 추가, `BootsSpecItem` 에 `siloNameKo`, `UniformSpecItem` 에 `clubNameKo` + `kitType` nullable
- **pytest 보강** — keywords 파싱, 사전 매칭 (silos/brands/clubs), uniform normalize, 시즌 정규식, kitType 영/한 추출
- **fixture HTML 신규** — `sample_uniform_product.html` (Manchester United Home/Away, 빈티지 1988/90 케이스)
- **ADR-017** — crawler 매칭 정책 (사전 구조 + keywords 파싱 + 매칭률 기준 + 한국어 alias 우선순위)
- **README 갱신** — `--category uniform` 사용법, brands/clubs 사전 갱신 가이드, 매칭률 기대치
- **smoke** — 실제 Kream 호출 30건 (축구화 15 + 유니폼 15) 매칭률 측정

### Out
- **crawler 리뷰 PR-B 잔여 (Major 4건)** — `defusedxml`, 부분 결과 보존 (partial.json), `except CrawlerBlockedError` re-raise, `_run_boots` SRP 분리 + `tests/test_cli.py`. 별도 후속 PR 로 (chore/crawler-safety-and-srp) — 본 PR 과 충돌 영역 없으므로 동시 진행 가능
- **PR-3 (`showcase.search_text` 합성)** — 별도 후속
- **PR-4 (`?keyword=` 검색 API)** — 별도 후속
- **Flutter 직접 입력 폼 단순화** — 별도 후속
- **alias 정정 API** (`BootsSpec.update()` / `UniformSpec.update()`) — Spec 레벨 update 가 필요해질 때 별도 PR

## 3. 변경 대상

### Python crawler (수정/신규)
- `tools/kream-crawler/kream_crawler/product_parser.py` — `parse_keywords()` 헬퍼 + `_meta(... "keywords")` + `RawProduct.name_ko`, `name_en` 채우기
- `tools/kream-crawler/kream_crawler/normalizer.py` — `to_uniform_item()` 신규, `to_bulk_import_item()` 카테고리 분기, `STUD_PATTERN` 에 `HG` 추가, `SURFACE_BY_STUD` 에 `HG` 추가, `match_brand()` / `match_club()` 신규, `extract_season()` / `extract_kit_type()` / `extract_kit_type_ko()` 신규
- `tools/kream-crawler/kream_crawler/cli.py` — `--category uniform` 분기 활성화, `_run_uniform()` 신규 (또는 통합 파이프라인), 통계 집계 보강 (club 매칭률, season 추출률)
- `tools/kream-crawler/kream_crawler/dictionaries/brands.yaml` — 신규 (Nike, Adidas, Puma, Mizuno, New Balance, Asics, Umbro, Diadora — 8개)
- `tools/kream-crawler/kream_crawler/dictionaries/clubs.yaml` — 신규 (EPL/LaLiga/Bundesliga 등 클럽 + 국가대표 30~40개)
- `tools/kream-crawler/kream_crawler/dictionaries/silos.yaml` — 한국어 alias 보강 (운영 후 누락 항목 추가)
- `tools/kream-crawler/pyproject.toml` — `package-data` 에 `clubs.yaml`, `brands.yaml` 추가

### tests (수정/신규)
- `tools/kream-crawler/tests/test_product_parser.py` — `parse_keywords()` 케이스 (3-token / 1-token / 빈 keywords)
- `tools/kream-crawler/tests/test_normalizer.py` — uniform `to_uniform_item()`, club/brand 매칭, 시즌 정규식, kitType 영/한, MG/HG 분기
- `tools/kream-crawler/tests/test_cli.py` — `--category uniform` 진입점 (NotImplementedError 해제 검증, 통계 키 검증)
- `tools/kream-crawler/tests/fixtures/sample_uniform_product.html` — 신규
- `tools/kream-crawler/tests/fixtures/sample_boots_product.html` — keywords 메타 추가 (한국어 + 영문)

### docs (신규/수정)
- `docs/architecture/adr/ADR-017-crawler-korean-matching-policy.md` — 신규
- `tools/kream-crawler/README.md` — `--category uniform` 사용법, 사전 갱신 가이드, 매칭률 기대치, ADR-017 링크

## 4. 접근

### keywords 메타 파싱

Kream HTML 의 `<meta name="keywords">` 형식 (실측):
```
"AT5889-174,나이키 프리미어 3 FG 화이트 메탈릭 골드,Nike Premier 3 FG White Metallic Gold"
```

```python
def parse_keywords(keywords: str) -> tuple[str | None, str | None, str | None]:
    """meta keywords 를 (modelCode, name_ko, name_en) 으로 분리.

    토큰 < 3 개 또는 빈 입력은 모두 None 으로 처리 (호출자가 fallback 책임).
    """
    if not keywords:
        return None, None, None
    parts = [p.strip() for p in keywords.split(",")]
    if len(parts) >= 3:
        return parts[0] or None, parts[1] or None, parts[2] or None
    if len(parts) == 1:
        return parts[0] or None, None, None
    return None, None, None
```

### 사전 — brands.yaml

```yaml
- canonical: "Nike"
  aliases: ["나이키"]
- canonical: "Adidas"
  aliases: ["아디다스"]
- canonical: "Puma"
  aliases: ["푸마"]
- canonical: "Mizuno"
  aliases: ["미즈노"]
- canonical: "New Balance"
  aliases: ["뉴발란스"]
- canonical: "Asics"
  aliases: ["아식스"]
- canonical: "Umbro"
  aliases: ["엄브로"]
- canonical: "Diadora"
  aliases: ["디아도라"]
```

### 사전 — clubs.yaml

```yaml
# EPL
- canonical: "Manchester United"
  aliases: ["맨체스터 유나이티드", "맨유"]
  league: "EPL"
- canonical: "Liverpool"
  aliases: ["리버풀"]
  league: "EPL"
- canonical: "Manchester City"
  aliases: ["맨체스터 시티", "맨시티"]
  league: "EPL"
- canonical: "Arsenal"
  aliases: ["아스날", "아스널"]
  league: "EPL"
- canonical: "Chelsea"
  aliases: ["첼시"]
  league: "EPL"
- canonical: "Tottenham"
  aliases: ["토트넘"]
  league: "EPL"
# LaLiga
- canonical: "Real Madrid"
  aliases: ["레알 마드리드", "레알"]
  league: "LaLiga"
- canonical: "Barcelona"
  aliases: ["바르셀로나", "바르샤"]
  league: "LaLiga"
- canonical: "Atletico Madrid"
  aliases: ["아틀레티코 마드리드", "아틀레티코"]
  league: "LaLiga"
# Bundesliga
- canonical: "Bayern Munich"
  aliases: ["바이에른 뮌헨", "뮌헨"]
  league: "Bundesliga"
- canonical: "Borussia Dortmund"
  aliases: ["도르트문트"]
  league: "Bundesliga"
# Serie A
- canonical: "Juventus"
  aliases: ["유벤투스"]
  league: "Serie A"
- canonical: "AC Milan"
  aliases: ["AC 밀란", "밀란"]
  league: "Serie A"
- canonical: "Inter Milan"
  aliases: ["인터 밀란", "인테르"]
  league: "Serie A"
# Ligue 1
- canonical: "Paris Saint-Germain"
  aliases: ["파리 생제르맹", "PSG"]
  league: "Ligue 1"
# 국가대표 (league: null)
- canonical: "Korea"
  aliases: ["대한민국", "한국"]
  league: null
- canonical: "England"
  aliases: ["잉글랜드"]
  league: null
- canonical: "Brazil"
  aliases: ["브라질"]
  league: null
- canonical: "Argentina"
  aliases: ["아르헨티나"]
  league: null
- canonical: "Germany"
  aliases: ["독일"]
  league: null
- canonical: "France"
  aliases: ["프랑스"]
  league: null
- canonical: "Spain"
  aliases: ["스페인"]
  league: null
- canonical: "Portugal"
  aliases: ["포르투갈"]
  league: null
- canonical: "Italy"
  aliases: ["이탈리아"]
  league: null
- canonical: "Netherlands"
  aliases: ["네덜란드"]
  league: null
- canonical: "Japan"
  aliases: ["일본"]
  league: null
```

### normalizer 시그니처

```python
def to_uniform_item(raw: RawProduct, clubs: list[Club], brands: list[Brand]) -> CatalogItem:
    name_en = raw.get("name_en") or raw.get("name") or ""
    name_ko = raw.get("name_ko") or ""

    club = match_club(name_en, name_ko, clubs)
    brand = match_brand(raw.get("brand"), name_ko, brands)
    season = extract_season(name_en) or extract_season(name_ko)
    kit_type = extract_kit_type(name_en) or extract_kit_type_ko(name_ko)  # nullable

    return CatalogItem(
        category="UNIFORM",
        brand=brand.canonical if brand else raw.get("brand"),
        modelCode=raw.get("style_code"),
        fullNameKo=name_ko or None,
        fullNameEn=name_en or None,
        uniformSpec=UniformSpecItem(
            clubName=club.canonical if club else None,
            clubNameKo=club.aliases[0] if club and club.aliases else None,
            season=season,
            league=club.league if club else None,
            kitType=kit_type,  # nullable — 빈티지/추출 실패 모두 None
        ),
    )
```

### 시즌 정규식

```python
SEASON_PATTERN = re.compile(r"(\d{2,4})[/-](\d{2,4})")

def extract_season(text: str) -> str | None:
    """'24/25', '24-25', '1988/90' 모두 매칭. 매칭 시 원형 그대로 반환."""
    if not text:
        return None
    m = SEASON_PATTERN.search(text)
    if not m:
        return None
    return f"{m.group(1)}/{m.group(2)}"
```

### kitType 추출 우선순위 (영문 → 한국어)

```python
KIT_TYPE_EN = {"HOME": "HOME", "AWAY": "AWAY", "THIRD": "THIRD", "3RD": "THIRD"}
KIT_TYPE_KO = {"홈": "HOME", "어웨이": "AWAY", "써드": "THIRD", "3rd": "THIRD"}
```

### 백엔드 contract 동기화 — TypedDict

```python
class CatalogItem(TypedDict, total=False):
    # 백엔드 BulkImportCatalogItemRequest.Item 거울 (PR #71 + PR #75)
    category: str
    brand: str
    modelCode: str | None
    officialImageUrl: str | None
    fullNameKo: str | None       # 신규 (PR #75 ADR-016)
    fullNameEn: str | None       # 신규 (PR #75 ADR-016)
    bootsSpec: BootsSpecItem | None
    uniformSpec: UniformSpecItem | None

class BootsSpecItem(TypedDict, total=False):
    studType: str | None         # FG/SG/AG/TF/IC/MG/HG (PR #75: MG/HG 추가)
    siloName: str | None
    siloNameKo: str | None       # 신규
    releaseYear: str | None
    surfaceType: str | None
    extraSpecJson: str | None

class UniformSpecItem(TypedDict, total=False):
    clubName: str | None
    clubNameKo: str | None       # 신규
    season: str | None
    league: str | None
    kitType: str | None          # nullable (PR #75 ADR-016 — 빈티지)
    extraSpecJson: str | None
```

## 5. 단계 (Steps)

### Step 1: keywords-extraction-and-fixture

**읽어야 할 파일**:
- `tools/kream-crawler/kream_crawler/product_parser.py`
- `tools/kream-crawler/tests/fixtures/sample_boots_product.html`
- `tools/kream-crawler/tests/test_product_parser.py`

**작업**:
- `parse_keywords(keywords: str)` 헬퍼 신규 — 콤마 split, 3-token 분리, 빈 입력 None 처리
- `parse_product_html()` 흐름에서 `_meta(soup, "keywords")` 호출 → `parse_keywords()` → `RawProduct.name_ko`, `RawProduct.name_en` 채움
- `RawProduct` TypedDict 에 `name_en` 필드 추가
- fixture `sample_boots_product.html` 에 `<meta name="keywords">` 토큰 추가 (실제 Kream 형식)

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_product_parser.py -v
```

**금지사항**:
- 기존 fixture 의 다른 메타 태그 변경 금지 (회귀 위험). keywords 만 추가
- `parse_keywords` 가 1-token 일 때도 modelCode 추정 금지 — 명세상 3-token 만 정상

### Step 2: dictionaries-brands-clubs-silos-stud

**읽어야 할 파일**:
- `tools/kream-crawler/kream_crawler/dictionaries/silos.yaml`
- `tools/kream-crawler/kream_crawler/normalizer.py` (STUD_PATTERN, SURFACE_BY_STUD, load_silos)
- `tools/kream-crawler/pyproject.toml` (package-data)

**작업**:
- `dictionaries/brands.yaml` 신규 — 위 §4 brands.yaml 참고 (8 brands)
- `dictionaries/clubs.yaml` 신규 — 위 §4 clubs.yaml 참고 (30~40 clubs + 국가대표)
- `silos.yaml` 한국어 alias 누락 항목 보강
- `normalizer.py`:
  - `STUD_PATTERN` 에 `HG` 추가
  - `SURFACE_BY_STUD` 에 `HG: "단단한 천연잔디"` 추가
  - `Brand`, `Club` frozen dataclass + `load_brands()`, `load_clubs()` 추가
  - `match_brand(brand_en, name_ko, brands) -> Brand | None`
  - `match_club(name_en, name_ko, clubs) -> Club | None` (longest match wins, alias 양방향)
- `pyproject.toml` `[tool.setuptools.package-data]` 에 `brands.yaml`, `clubs.yaml` 추가

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_normalizer.py -v
.venv/bin/python -c "from kream_crawler.normalizer import load_brands, load_clubs; print(len(load_brands()), len(load_clubs()))"
```

**금지사항**:
- 클럽/브랜드 사전에 alias 중복 금지 (한 alias 가 여러 club 에 매칭되면 결정성 깨짐)
- `MG/HG` 매핑을 `surfaceType` 자유 문자열로 흡수 금지 — `StudType` 정규값 유지

### Step 3: normalizer-uniform-and-extractors

**읽어야 할 파일**:
- Step 2 산출물 — `Brand`, `Club`, `match_brand`, `match_club`
- `tools/kream-crawler/kream_crawler/normalizer.py`
- `tools/kream-crawler/tests/test_normalizer.py`

**작업**:
- `extract_season(text) -> str | None` — `\d{2,4}[/-]\d{2,4}` 정규식, 매칭 시 `"{a}/{b}"` 반환
- `extract_kit_type(text) -> str | None` — `HOME/AWAY/THIRD/3RD` 영문 매칭
- `extract_kit_type_ko(text) -> str | None` — `홈/어웨이/써드/3rd` 한국어 매칭
- `to_uniform_item(raw, clubs, brands) -> CatalogItem` — 위 §4 시그니처 그대로
- `to_bulk_import_item(raw, silos, clubs, brands)` 디스패치 — `category_path` 가 uniform 이면 `to_uniform_item`, 아니면 기존 boots
- `BootsSpecItem` 에 `siloNameKo`, `UniformSpecItem` 에 `clubNameKo` + `kitType` nullable 명시
- 단위 테스트: 빈티지 (kitType None), 국가대표 (league null), 한국어만 매칭, 영문만 매칭, 시즌 다양 변형

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_normalizer.py -v
```

**금지사항**:
- `extract_kit_type` 의 매칭 우선순위를 입력 위치에 의존시키지 마라 — 영문 우선, 영문 None 시 한국어 fallback (테스트로 명세 보호)
- `kitType` 추출 실패 시 예외 던지지 마라 — None 반환 (ADR-016: nullable 정책)

### Step 4: cli-uniform-support-and-stats

**읽어야 할 파일**:
- `tools/kream-crawler/kream_crawler/cli.py`
- `tools/kream-crawler/kream_crawler/sitemap.py`
- Step 3 산출물

**작업**:
- `cli.py:61-62` 의 `NotImplementedError("uniform")` 해제
- `_run_uniform(args)` 신규 — sitemap 에서 uniform 카테고리 URL 발견 → 페치 → parse → `to_uniform_item` → export
  - sitemap 측에 `discover_uniform_product_urls()` 가 없으면 keyword 기반 검색 (예: "유니폼", "저지", "jersey")
- 통계 집계 보강 — `club_matched`, `season_extracted`, `kit_type_inferred` (각 카운트)
- `_run_boots` 와 `_run_uniform` 의 공통 부분 (HTTP 클라이언트, export, 통계 출력) 은 helper 로 추출 (중복 최소화)
- `argparse` 의 `choices=["boots", "uniform"]` 로 갱신

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_cli.py -v
```

**금지사항**:
- `_run_uniform` 안에서 cli 가 `uniformSpec` 의 dict 키 형태를 직접 알면 안 됨 — 통계 집계는 normalizer 의 helper (예: `compute_match_stats(items)`) 에 위임
- sitemap keyword 검색에 약관 위반 키워드 (예: "정품 인증") 사용 금지

### Step 5: backend-contract-typed-dict

**읽어야 할 파일**:
- `tools/kream-crawler/kream_crawler/normalizer.py` (TypedDict 정의)
- `tools/kream-crawler/tests/test_exporter.py`
- main 의 `backend/.../CreateCatalogItemRequest.java` + `BulkImportCatalogItemRequest.java` (PR #75 머지본)

**작업**:
- `CatalogItem` TypedDict 에 `fullNameKo`, `fullNameEn` 추가
- `BootsSpecItem` 에 `siloNameKo` 추가
- `UniformSpecItem` 에 `clubNameKo` 추가, `kitType` 의 docstring 에 nullable 명시
- 모든 dict 생성 위치 (`to_bulk_import_item`, `to_uniform_item`) 에서 신규 필드 채움
- 백엔드 클래스 fully-qualified 이름을 `normalizer.py` 모듈 docstring 에 주석으로 추적 가능하게 명시:
  ```python
  # Mirror of:
  # - com.gearshow.backend.catalog.adapter.in.web.dto.BulkImportCatalogItemRequest.Item (PR #71)
  # - com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand fields (PR #75 ADR-016)
  ```
- `tests/test_exporter.py` 에 신규 필드 round-trip 검증 추가

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_exporter.py tests/test_normalizer.py -v
```

**금지사항**:
- TypedDict `total=False` 를 `total=True` 로 바꾸지 마라 — 모든 신규 필드는 nullable 정책

### Step 6: adr-017-and-readme

**작업**:
- `docs/architecture/adr/ADR-017-crawler-korean-matching-policy.md` 신규
  - **Context**: PR #75 ADR-016 의 catalog 한국어 alias 컬럼을 crawler 가 어떻게 채우는가
  - **D1**: keywords 메타 파싱 — 3-token 분리 + 1/0-token fallback null
  - **D2**: 사전 구조 — `silos.yaml` (사일로 + brand), `brands.yaml` (브랜드 + 한국어 alias), `clubs.yaml` (클럽 + 한국어 alias + league)
  - **D3**: 매칭 알고리즘 — longest alias match wins, 동률 시 canonical 알파벳 정렬 (결정성)
  - **D4**: 매칭률 기준 — 축구화 silo ≥70%, 유니폼 club ≥70%, season ≥80%, brand ≥95%. 하한 미달 시 사전 보강 또는 페이로드 점검
  - **D5**: kitType nullable 정책 — ADR-016 D3 와 동일 (빈티지/추출 실패 모두 None, 운영 메트릭으로 분리)
- `tools/kream-crawler/README.md` — `--category uniform` 사용법, 사전 갱신 가이드, 매칭률 기대치, ADR-017 링크

**AC**:
```bash
test -f docs/architecture/adr/ADR-017-crawler-korean-matching-policy.md
grep -q "uniform" tools/kream-crawler/README.md
```

### Step 7: smoke-real-kream

**작업**:
- 실제 Kream 호출 30건 (축구화 15 + 유니폼 15) — `--rate-limit 1.0` 준수
- 매칭률 측정:
  - 축구화 silo 매칭률 ≥ 70%
  - 유니폼 club 매칭률 ≥ 70%
  - season 추출률 ≥ 80%
  - brand 매칭률 ≥ 95%
- 측정 결과를 `tools/kream-crawler/SMOKE-RESULT.md` (gitignore) 에 기록 (커밋 X — 운영 데이터)
- 하한 미달 시 사전 보강 또는 normalizer 보강 후 재측정

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/python -m kream_crawler --category boots --limit 15 --output /tmp/boots-smoke.json --rate-limit 1.0
.venv/bin/python -m kream_crawler --category uniform --limit 15 --output /tmp/uniform-smoke.json --rate-limit 1.0
```

**금지사항**:
- smoke 결과 JSON 또는 raw HTML 을 git 에 커밋 금지 (운영 데이터 + 약관 리스크)
- 30건 초과 호출 금지

## 6. 테스트 계획

- **단위 (pytest)**: keywords 파싱, 사전 매칭 (silos/brands/clubs), studType MG/HG, kitType 영/한, 시즌 정규식 변형, uniform normalize, 빈티지/국가대표 케이스
- **단위 (cli)**: `--category uniform` 진입점, NotImplementedError 해제, 통계 키 검증
- **통합**: fixture HTML 기반 end-to-end (parse → normalize → export → JSON Schema 검증)
- **smoke (수동)**: 실제 Kream 호출 30건 매칭률 (Step 7)
- **회귀 차단**: 기존 boots 흐름의 모든 테스트 통과 + `silos.yaml` 30개 사일로 sanity

## 7. 완료 기준

```bash
cd tools/kream-crawler
.venv/bin/pytest -v
.venv/bin/ruff check kream_crawler tests
test -f docs/architecture/adr/ADR-017-crawler-korean-matching-policy.md
grep -q "uniform" tools/kream-crawler/README.md
```

추가:
- [ ] code-reviewer Critical 0 + Major 0
- [ ] architecture-reviewer Critical 0
- [ ] test-writer 의 cli.py 테스트 누락 (PR #74 P0) 본 PR 에서 신규로 해결
- [ ] CodeRabbit 통과
- [ ] 실제 Kream smoke — 매칭률 하한 모두 충족
- [ ] EXEC_PLAN Status `completed`

## 8. 롤백 전략

- crawler 자체는 백엔드 의존이 없으므로 (`POST /api/admin/catalog/bulk-import` 페이로드 형태만 일치하면 됨) 코드 revert 만으로 충분
- 사전 (`brands.yaml`, `clubs.yaml`) 은 신규 파일 → revert 시 함께 제거
- `STUD_PATTERN` 에 `HG` 추가는 PR #75 의 `StudType.HG` 추가와 정합 — revert 시 `MG/HG` 모두 silos.yaml 에서 빠질 수 있으나 PR #75 backend 가 nullable 처리하므로 catalog import 자체는 동작
- ADR-017 은 단방향 — revert 보다 ADR-018 (또는 ADR-017 v1.1) 으로 갱신

## 9. 의존 / 가정

- main 에 PR #74 (kream-crawler) + PR #75 (catalog-search-foundation) 머지 완료 (확인됨 — base commit `32cacf8`)
- 후속 PR-B (crawler 리뷰 Major 4건 — defusedxml / 부분 결과 / except 리팩토링 / cli SRP) 는 본 PR 과 충돌 영역 없음 — 동시 진행 OK
- Kream 약관 12조 / robots.txt / 1 req/sec 정책 그대로 준수
