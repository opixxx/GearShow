# ADR-023: Catalog 크롤러 다중 출처 추상화 + crazy11 출처 추가

- **Status**: Accepted
- **Date**: 2026-05-11
- **Deciders**: opix
- **Related**: ADR-017 (Kream crawler 한국어 매칭 정책), ADR-021 §D7 (외부 사이트 식별자 유지), ADR-022 (silos 시리즈 통일), PR #82 PoC, PR #86 fix

> §D1 의 crazy11 카테고리 매핑은 메인 nav (홈페이지 상단 탭) 기준으로 한정 — 시즌오프/베스트셀러는 별도 view 옵션이라 같은 상품 중복 + 사이드 link 오염 위험. PR #90 PoC (limit=20) 에서 카테고리 오염 50% (12 중 6건 = 가방/축구공/의류/슬리퍼) 발견 → 후속 fix PR 에서 매핑 단순화 (BOOTS 8→2개, UNIFORM 2 유지) + discover 의 URL xcode 후처리 필터 추가로 보강.

## Context

PR #82~#86 시점까지 catalog 크롤러는 **Kream 단일 출처** 만 지원했다. 운영 적재 100건 검증 후 다음 한계가 명확해짐:

1. **단일 출처 의존성** — Kream 정책 변경, robots.txt 차단 등 외부 변동 위험 전가.
2. **신상품 지연** — Kream 의 검색 결과 SSR HTML 에 노출되는 상품 수가 한계 (boots 145, 본 도구로 50건 추출 한계).
3. **사용자 요청** — 신상품이 빠르게 등장하는 사이트(crazy11) 추가 필요.

본 ADR 작성 직전 정찰 결과:
- **capostore (capostore.co.kr)**: robots.txt 가 모든 봇 전면 차단 → 운영 정책상 진입 불가 거부.
- **crazy11 (crazy11.co.kr)**: robots.txt 일반 허용 (특정 xcode 만 차단), sitemap 정상, 272개 상품, EUC-KR + JSON-LD Product schema 기반. 진입 가능.

## Decision

### D1. SourceClient ABC — `sources/<site>.py` 분리

```python
# catalog_crawler/sources/__init__.py
class SourceClient(ABC):
    name: ClassVar[str]              # 'kream' / 'crazy11'
    base_url: ClassVar[str]

    @abstractmethod
    def discover_product_urls(self, category: Category, limit: int) -> list[str]: ...

    @abstractmethod
    def fetch_product(self, url: str) -> requests.Response: ...

    @abstractmethod
    def parse_product(self, response, source_url) -> RawProduct: ...
```

- 기존 `KreamClient`/`KREAM_BASE`/`sitemap.py`/`product_parser.py` 의 Kream 로직 → `sources/kream.py` 로 이동.
- 신규 `sources/crazy11.py` 가 ABC 구현.
- `Category` enum (BOOTS / UNIFORM) 신설 — 출처 공통 분류.

### D2. CLI `--source` 옵션 — default kream

```bash
# 기존 동작 그대로 (default kream)
python -m catalog_crawler --category boots --limit 30 --output out.json

# crazy11 출처
python -m catalog_crawler --source crazy11 --category boots --limit 30 --output out.json
```

선택지 dispatch — 기존 흐름 (--source 미지정) 100% 보존. 회귀 위험 0.

### D3. 사이트별 robots.txt 준수 강제

| 사이트 | 차단 정책 |
|--------|----------|
| Kream | `/my/**`, `/history/**` — client 단 거부 (ADR-017) |
| crazy11 | `xcode=259`, `xcode=252` — client 단 `ForbiddenPathError` 거부 |

robots.txt 가 전면 차단인 사이트(capostore)는 추가 거부 (D6 참조).

### D4. `fullNameEn=null` 정책 수용 — crazy11 한계 흡수

crazy11 의 상품 상세 페이지에는 **영문 상품명 단일 source 가 부재**:
- og:title — 사이트 brand 명만 ("축구화는 역시 CRAZY11")
- title 태그 — 한국어
- JSON-LD Product.name — 한국어

→ `RawProduct.name_en=None` 반환. backend 의 `catalog_item.full_name_en` 컬럼은 이미 nullable (PR #75 ADR-016 §B2) 이라 backend 변경 0.

**영향**: 운영 catalog 의 fullNameEn=null 행은 `showcase.search_text` 합성 시 영문 키워드 매칭이 약화 — `name_ko` / `brand` / `modelCode` 등 다른 컬럼으로 부분 매칭 가능. ADR-018 §D2 의 search_text 합성 정책 그대로 동작.

### D5. 인코딩 정책 — source 별 fetch 메서드에서 처리

| 사이트 | 인코딩 | 처리 |
|--------|--------|------|
| Kream | UTF-8 (default) | 기본 동작 |
| crazy11 | **EUC-KR** | `Crazy11Client.fetch_product` 가 `response.encoding = "euc-kr"` 명시 |

application 레이어 (cli, normalizer) 변환 금지 — source 별 client 에 캡슐화.

### D6. capostore 거부 결정 — 운영 정책 강제

capostore robots.txt 가 모든 봇 전면 차단 (`User-agent: *` + `Disallow: /`). 명시된 검색엔진 봇(Googlebot/NaverBot 등) 만 예외. 우리 봇은 차단 대상이므로:

- **거부** — ADR-017 의 robots.txt 준수 + Anti-bot 우회 시도 금지 정책 정합.
- 대안: 사이트 운영자와 명시적 협의/제휴 (시간 소요), 또는 다른 사이트 우선 (D1 추상화 기반으로 추가).

### D7. brand 신뢰성 — JSON-LD 신뢰 X, normalizer 의 brands.yaml 위임

crazy11 의 JSON-LD `Product.brand.name` 이 사이트의 할인율 등 잘못된 값 (예: `"38%"`) 으로 채워짐. 신뢰 불가.

→ `Crazy11Client.parse_product` 가 `brand=None` 반환. `normalizer.match_brand` 가 brands.yaml 한국어 alias 매칭으로 보강 — 기존 흐름 그대로.

### D8. backward-compat shim 유지

기존 `from catalog_crawler.http_client import KreamClient` 같은 import path 호환을 위해:
- `http_client.py`, `sitemap.py`, `product_parser.py` 는 `sources/kream.py` 에서 re-export 하는 shim.
- 본 PR 시점 외부 사용처 0건 (도구 내부만 영향) 이지만 향후 다른 도구가 의존할 가능성 대비.
- cli.py 의 `discover_boots_product_urls`/`discover_uniform_product_urls`/`parse_product_html` 도 module-level wrapper 로 유지 — 테스트 monkey-patch 호환.

## 대안 검토

- **(B) capostore 우회** — 운영 정책 + 법적 위험 (정보통신망법, 부정경쟁방지법, 잡코리아 vs 사람인 판례 등). ❌
- **(C) Kream 단일 출처 지속** — 단일 의존성 + 신상품 지연. ❌
- **(D) 제조사 공식 (Adidas/Nike/Puma) 직접 수집** — SPA 자동화 난이도, 사이트별 인증 등. 별도 트랙으로 보류. ❌ (본 PR 범위)
- **(E) 무신사/29CM 등 추가** — 본 PR 의 D1 추상화 base 위에서 추후 PR 로 추가 가능.

## 트레이드오프

| 영역 | 비용 | 이득 |
|------|------|------|
| crawler 모듈 구조 | `sources/` 디렉토리 추가, ABC + shim 도입 | 신규 사이트 추가 비용 ↓ — 1 모듈 + 테스트만 |
| 영문명 단일 source 부재 (crazy11) | fullNameEn=null 행 증가 → 영문 검색 매칭 약화 | 한국어 검색·필터링은 100% 동작 |
| EUC-KR 처리 | source 별 fetch 메서드 분기 | 사이트 다양성 확보 |
| backward-compat shim | http_client.py 등 wrapper 모듈 잔존 | 외부 사용처 회귀 위험 0, 점진 제거 가능 |

## 후속 작업

- **무신사 / 29CM 추가** — robots.txt 정찰 후 별도 PR. 본 PR 의 D1 추상화가 base.
- **제조사 공식 직접 수집** — Adidas/Nike Korea 등. SPA 자동화 (Playwright) 검토.
- **사이트별 약관 검토 audit** — Kream 12조 2항 + crazy11 약관 + 신규 사이트 추가 시 ADR 보강.
- **`http_client.py`/`sitemap.py`/`product_parser.py` shim 제거** — 외부 사용처 0건 확인 후 후속 cleanup PR.
- **crazy11 영문명 추론** — 제조사 모델코드 기반 역검색 등. 별도 트랙.
- **카테고리 매핑 자동화** — 현재 `_CATEGORY_XCODES` 가 수동 정찰 결과 hardcode. 신규 카테고리 발견 시 yaml 분리 검토.
