# EXEC_PLAN: kream-crawler-hardening

- **Type**: refactor
- **Status**: in_progress
- **Risk**: Caution (보안/안전성 + 사전 회귀 보호 + Python 도구 일괄)
- **Created**: 2026-05-04
- **Branch**: refactor/kream-crawler-hardening
- **Worktree**: /Users/opix/gearshow-kream-crawler-hardening
- **Port**: 9000
- **Base**: main 의 PR #74/#75/#76/#77 머지 후 (`bae86d5`)

---

## 1. 목표

PR #76 (`feature/kream-crawler-uniform-ko`) 내부 리뷰의 잔여 9 Major 항목을 일괄 처리. crawler 의 보안 강화 (XXE 차단), 운영 안정성 (부분 결과 보존, fail-fast), 사전 회귀 보호 강도 (silos 30 + brands 8 + clubs 32 모두 parametrize), 책임 분리 (compute_match_stats typed contract + 한글 범위 보강) 를 한 PR 로 정리.

PR #74 부터 잔존하던 P0 (silos 30 개 중 3 개만 보호) 와 PR #76 의 동일 답습 (brands/clubs) 모두 본 PR 로 해소.

## 2. 범위

### In
- **defusedxml 도입** (code-reviewer Major #2, PR #74 부터 잔존) — XXE/billion laughs 방어. `xml.etree.ElementTree` → `defusedxml.ElementTree` 교체. pyproject.toml 의존성 추가.
- **부분 결과 보존 (`partial.json`)** (code-reviewer Major #3) — `_run_pipeline` 의 fetch 루프에서 `CrawlerBlockedError`/`ForbiddenPathError` 가 propagate 되기 전에 `try/finally` 로 그때까지 모은 items 를 `output.with_suffix(".partial.json")` 로 dump. 부분 검수 가능.
- **`compute_match_stats` typed contract** (architecture-reviewer Major #2) — dict 반환 → `MatchStats(NamedTuple)` 또는 `@dataclass(frozen=True)`. cli 가 dict 키 형태에 결합되지 않도록 attribute access 로 전환. 기존 export 의 stats dict 호환은 `_asdict()` 로 유지.
- **`_extract_korean_alias` 한글 범위 확장** (code-reviewer Major #1) — BMP 외 자모(U+1100-11FF) / 호환 자모(U+3130-318F) 까지 인식. **alias 순서 의존 제거** — 첫 한글 alias 가 아니라 한글이 가장 많이 포함된 alias 선택 (yaml 순서 변경에 무관).
- **사전 parametrize 회귀 보호** (test-writer Major) — `silos.yaml` 30개 / `brands.yaml` 8개 / `clubs.yaml` 32개 alias 매칭을 `@pytest.mark.parametrize` 로 모두 보호. 운영 후 alias 추가 시 yaml 수정만으로 회귀 차단.
- **AG/IC StudType parametrize** (test-writer Major) — `TestExtractStudType` 의 7개 케이스 (FG/SG/AG/TF/IC/MG/HG) 명시 분리.
- **매칭 정책 docstring** (code-reviewer Major #4 부분) — `match_brand` (영문 우선/한국어 fallback), `match_club` (영/한 양쪽), `match_silo` (단일 haystack) 의 비대칭 정책을 각 함수 docstring 에 명시.
- **TypedDict contract 추적 보강** (PR #76 architecture-reviewer Major + code-reviewer S1) — `normalizer.py` docstring 의 백엔드 클래스 fully-qualified 주석 강화. 자동 검증 (jsonschema) 은 본 PR 범위 밖 — ADR 후속 작업으로 명시.

### Out
- **TypedDict ↔ Java DTO 자동 회귀 검증** (code-reviewer S1, architecture-reviewer Major #1) — backend 빌드 시 OpenAPI/JSON Schema export → crawler test 가 jsonschema 로 검증. 양방향 보호 메커니즘. **본 PR 범위 밖** — backend 빌드 hook 도입이 필요해 별도 PR (ADR-019 또는 후속). 본 PR 은 docstring 추적 강화로 충당.
- **`KreamClient` SRP 분리** (PR #74 의 코드-리뷰어 Major) — RateLimiter / PathPolicy / BlockedStatusGuard 책임 분리. 본 PR 범위 밖 — 멀티스레드 race 위험은 단일 스레드 도구라 즉시 위협 아님. 백로그.
- **`KIT_TYPE_KO_MAP` prefix 충돌 회귀 테스트** (code-reviewer Minor) — 백로그.
- **PR-3 / PR-4 (검색)** — 검색 흐름은 별개 트랙.

## 3. 변경 대상

### Python crawler (수정)
- `tools/kream-crawler/pyproject.toml` — `defusedxml>=0.7` 의존성 추가
- `tools/kream-crawler/kream_crawler/sitemap.py` — `xml.etree.ElementTree` → `defusedxml.ElementTree`
- `tools/kream-crawler/kream_crawler/cli.py` — `_run_pipeline` 의 try/finally 로 partial.json 보존, fatal 예외 propagate 전 dump
- `tools/kream-crawler/kream_crawler/normalizer.py`:
  - `MatchStats` NamedTuple 신규 (또는 frozen dataclass)
  - `compute_match_stats` 반환 타입 NamedTuple 로 변경
  - `_extract_korean_alias` 한글 범위 확장 + 순서 의존 제거 (가장 많은 한글 포함 alias 선택)
  - `match_brand` / `match_club` / `match_silo` docstring 보강
  - 모듈 docstring 의 백엔드 contract 추적 주석 강화

### Python tests (수정/신규)
- `tools/kream-crawler/tests/test_sitemap.py` — defusedxml 사용 검증 (XXE payload 차단)
- `tools/kream-crawler/tests/test_cli.py` — partial.json 보존 회귀 테스트 (CrawlerBlockedError 도중 발생 시 부분 결과 파일 생성 확인)
- `tools/kream-crawler/tests/test_normalizer.py`:
  - `MatchStats` NamedTuple 의 attribute access 회귀
  - `_extract_korean_alias` 자모/호환 자모/순서 의존 제거 케이스
  - `silos.yaml` 30개 alias 모두 ParameterizedTest 매칭 (brand 정합성 포함)
  - `brands.yaml` 8개 한국어 alias 매칭 (Umbro/Diadora/New Balance/Asics 포함)
  - `clubs.yaml` 32개 alias 매칭 (Liverpool/Real Madrid/Bayern/AC Milan/PSG/Brazil/Argentina 등)
  - `TestExtractStudType` AG/IC 추가

### docs (수정)
- `docs/architecture/adr/ADR-017-crawler-korean-matching-policy.md` — §후속 작업에 jsonschema 자동 검증 + KreamClient SRP 백로그 메모

## 4. 접근

### `MatchStats` NamedTuple

```python
from typing import NamedTuple

class MatchStats(NamedTuple):
    """ADR-017 §D4 매칭률 통계 — typed contract.

    cli 가 dict 키 형태에 결합되지 않도록 NamedTuple 로 표현. 기존 export 의 stats
    dict 와의 호환은 ``stats._asdict()`` 로 유지. NamedTuple 은 frozen + immutable +
    typed 라 IDE/mypy 가 키 변경을 즉시 감지.
    """
    total: int
    silo_matched: int
    club_matched: int
    season_extracted: int
    kit_type_inferred: int
    brand_matched: int
    korean_full_name_present: int


def compute_match_stats(items: list[CatalogItem]) -> MatchStats:
    boots_total = sum(1 for it in items if it.get("category") == "BOOTS")
    uniform_total = sum(1 for it in items if it.get("category") == "UNIFORM")
    ...
    return MatchStats(
        total=len(items),
        silo_matched=...,
        ...
    )
```

cli 호출부:
```python
match_stats = compute_match_stats(items)
stats = {
    "category": category,
    "candidateUrls": len(candidate_urls),
    "fetched": fetched,
    "parseFailed": parse_failed,
    **match_stats._asdict(),   # 기존 export schema 호환
}
LOGGER.info(
    "[%s] silo=%d/%d, club=%d/%d, ...",
    category, match_stats.silo_matched, match_stats.total,
    match_stats.club_matched, match_stats.total, ...
)
```

### `_extract_korean_alias` 한글 범위 + 순서 무관

```python
def _is_korean_char(ch: str) -> bool:
    code = ord(ch)
    return (
        0xAC00 <= code <= 0xD7A3   # 한글 음절 (가-힣)
        or 0x1100 <= code <= 0x11FF  # 한글 자모
        or 0x3130 <= code <= 0x318F  # 한글 호환 자모
    )


def _korean_char_count(s: str) -> int:
    return sum(1 for c in s if _is_korean_char(c))


def _extract_korean_alias(entry: Silo | Club | None) -> str | None:
    """alias 들 중 한글 문자가 가장 많이 포함된 항목 반환 (없으면 None).

    yaml 순서에 의존하지 않도록 한글 char count 기준 정렬. 동률 시 첫 매칭 (yaml 순서 유지).
    한글 음절 + 자모 + 호환 자모 모두 인식 — 향후 사전이 자모/한자 alias 를 가질 수 있음.
    """
    if entry is None:
        return None
    candidates = [(a, _korean_char_count(a)) for a in entry.aliases]
    candidates = [(a, n) for a, n in candidates if n > 0]
    if not candidates:
        return None
    candidates.sort(key=lambda x: -x[1])  # 한글 가장 많은 alias 우선
    return candidates[0][0]
```

### partial.json 보존

```python
def _run_pipeline(args, *, category, discover, normalize, ...) -> int:
    items: list[CatalogItem] = []
    ...
    try:
        for url in candidate_urls:
            try:
                response = client.get(url)
            except (CrawlerBlockedError, ForbiddenPathError):
                # 정책 위반 — 부분 결과 dump 후 propagate
                _dump_partial(items, args.output, category)
                raise
            ...
    except Exception:
        # 예상치 못한 에러도 부분 결과 보존
        _dump_partial(items, args.output, category)
        raise
    ...

def _dump_partial(items, output_path, category):
    if not items:
        return
    partial_path = output_path.with_suffix(".partial.json")
    export(items, partial_path, stats={"partial": True, "category": category, "items": len(items)})
    LOGGER.warning("부분 결과 저장 → %s (%d 건)", partial_path, len(items))
```

### XXE 방어 (defusedxml)

```python
# pyproject.toml
dependencies = [
    "requests>=2.31",
    "beautifulsoup4>=4.12",
    "lxml>=5.0",
    "pyyaml>=6.0",
    "defusedxml>=0.7",       # 신규
]

# sitemap.py
from defusedxml import ElementTree as ET   # 기존 xml.etree.ElementTree 대체
```

`defusedxml.ElementTree` 는 entity expansion / external entity / billion laughs 를 자동 차단. API 는 `ElementTree` 와 동일.

### 사전 parametrize 패턴

```python
import yaml
from importlib import resources

def _load_silos_data():
    text = resources.files("kream_crawler.dictionaries").joinpath("silos.yaml").read_text(encoding="utf-8")
    return yaml.safe_load(text)


@pytest.mark.parametrize(
    "entry",
    _load_silos_data(),
    ids=lambda e: e["canonical"],
)
def test_silos_yaml_each_alias_matches_canonical(entry):
    """silos.yaml 각 entry 의 alias 가 match_silo() 로 정확히 canonical 을 반환하는가."""
    silos = load_silos()
    expected = entry["canonical"]
    for alias in entry["aliases"]:
        result = match_silo(alias, silos)
        assert result is not None, f"alias '{alias}' 매칭 실패"
        assert result.canonical == expected, f"alias '{alias}' → {result.canonical} (expected {expected})"
```

`brands.yaml` / `clubs.yaml` 도 동일 패턴.

## 5. 단계 (Steps)

### Step 1: defusedxml + partial.json + 매칭 정책 docstring

**작업**:
- `pyproject.toml` 에 `defusedxml>=0.7`
- `sitemap.py` 의 `xml.etree.ElementTree` → `defusedxml.ElementTree`
- `cli._run_pipeline` 에 `_dump_partial` + try/finally
- `match_silo/brand/club` docstring 에 정책 비대칭 명시
- 단위 테스트:
  - `test_sitemap` 에 XXE payload 차단 검증 1건
  - `test_cli` 에 partial.json 생성 검증 1건 (CrawlerBlockedError 발생 시)

**AC**:
```bash
cd tools/kream-crawler
.venv/bin/pytest tests/test_sitemap.py tests/test_cli.py -v
```

### Step 2: MatchStats NamedTuple + cli 갱신

**작업**:
- `normalizer.py` 에 `MatchStats(NamedTuple)` 정의
- `compute_match_stats` 반환 타입 변경
- `cli._run_pipeline` 의 stats dict 합치기 + 로그 출력 갱신 (`stats.silo_matched` attribute access)
- 단위 테스트 `test_compute_match_stats` 갱신 — attribute access 검증

**AC**:
```bash
.venv/bin/pytest tests/test_normalizer.py::TestComputeMatchStats tests/test_cli.py -v
```

### Step 3: _extract_korean_alias 한글 범위 + 순서 무관

**작업**:
- `_is_korean_char` 헬퍼 신규 (음절/자모/호환 자모)
- `_extract_korean_alias` 한글 char count 정렬
- 단위 테스트:
  - 자모 alias (`"ㄱㄴㄷ" 같은`) 인식 검증
  - 영문 첫 alias + 한국어 두 번째 alias 인 케이스 → 한국어 추출
  - 한국어 두 alias 중 char count 더 많은 것 우선

**AC**:
```bash
.venv/bin/pytest tests/test_normalizer.py -k "korean_alias" -v
```

### Step 4: 사전 parametrize 회귀 보호

**작업**:
- `silos.yaml` 30개 entry 의 모든 alias → match_silo 회귀 테스트
- `brands.yaml` 8개 → match_brand 회귀
- `clubs.yaml` 32개 → match_club 회귀
- `TestExtractStudType` parametrize 에 AG/IC 명시 케이스 추가

**AC**:
```bash
.venv/bin/pytest tests/test_normalizer.py -v
```

### Step 5: ADR-017 보강 + 최종 검증

**작업**:
- `ADR-017 §후속 작업` 에 jsonschema 자동 검증 + KreamClient SRP 백로그 메모
- 전체 pytest 통과 + ruff 미적용 (PR #74 와 동일 정책)

**AC**:
```bash
.venv/bin/pytest -v
```

## 6. 테스트 계획

- **단위**: defusedxml XXE 차단, partial.json 생성, MatchStats attribute access, _extract_korean_alias 자모/순서, 사전 parametrize 70+ 케이스
- **회귀**: 기존 117 tests 통과
- **통합 (수동, optional)**: 실제 fixture HTML 로 fetch loop → CrawlerBlockedError 시 partial.json 확인

## 7. 완료 기준

```bash
cd tools/kream-crawler
.venv/bin/pytest -v   # 전체 통과 (목표 ~150건)
```

추가:
- [ ] code-reviewer Critical 0 + Major 0
- [ ] architecture-reviewer Critical 0
- [ ] test-writer 의 PR #74/#76 P0 (사전 보호 부족) 본 PR 에서 해소
- [ ] CodeRabbit 통과
- [ ] EXEC_PLAN Status `completed`

## 8. 롤백 전략

- 모든 변경이 crawler 내부 — backend 영향 0
- 코드 revert 만으로 충분
- defusedxml 의존성은 `pyproject.toml` revert + `pip install -e ".[dev]"` 재설치

## 9. 의존 / 가정

- main 에 PR #74/#75/#76/#77 모두 머지 완료 (확인됨 — base `bae86d5`)
- 본 PR 은 검색 흐름과 독립 — 메모리 §단기 PR 옵션 의 PR-B 에 해당. PR-3 머지 후 PR-4 진행과 병행 가능.
- venv 신규 셋업 필요 — `python3 -m venv .venv && .venv/bin/pip install -e ".[dev]"`
