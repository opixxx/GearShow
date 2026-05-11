"""crazy11.co.kr 출처 (ADR-023).

축구화·풋살화·유니폼 전문몰. robots.txt 일반 크롤링 허용 (특정 xcode 만 차단).

데이터 형식 (Kream 과 다름):
- HTML 인코딩: **EUC-KR** (Kream UTF-8) — ``fetch_product`` 에서 명시
- 핵심 metadata: ``application/ld+json`` Product schema — name(한글), sku(modelCode), image
- 영문명 단일 source 없음 → ``name_en=None`` (ADR-023 §D4)
- brand: JSON-LD 의 ``brand.name`` 이 사이트의 할인율 등 잘못된 값으로 채워짐 — 신뢰 X.
  ``brand=None`` 반환, ``normalizer.match_brand`` 가 brands.yaml 한국어 alias 매칭으로 보강.

robots.txt 차단:
- ``xcode=259``, ``xcode=252`` (특정 카테고리) — client 단에서 ``ForbiddenPathError`` 거부.
"""

from __future__ import annotations

import json
import logging
import re
import time
from typing import ClassVar
from urllib.parse import parse_qs, urlparse

import requests
from bs4 import BeautifulSoup

from catalog_crawler.sources import (
    Category,
    CrawlerBlockedError,
    ForbiddenPathError,
    RawProduct,
    SourceClient,
)

LOGGER = logging.getLogger(__name__)

CRAZY11_BASE = "http://www.crazy11.co.kr"

DEFAULT_USER_AGENT = "GearShow-Catalog-Bot/1.0 (+contact: opix0306@naver.com)"

# 카테고리 xcode 매핑 — 메인 nav (홈페이지 상단 탭) 기준 (사용자 정찰 + 메인 페이지 HTML 분석).
# 시즌오프/베스트셀러 카테고리는 별도 view 옵션이라 매핑에서 제외 — 같은 상품 중복 +
# 카테고리 페이지 사이드 link 오염 위험 (PR #90 PoC limit=20 에서 50% 오염 발견).
_CATEGORY_XCODES: dict[Category, tuple[str, ...]] = {
    Category.BOOTS: ("257", "243"),       # 축구화 + 풋살화
    Category.UNIFORM: ("175", "292"),     # 단체/유니폼 + 프리미어 리그
}

# robots.txt 가 명시적으로 차단한 xcode. 검색·sitemap 결과에서도 후처리 필터.
_DISALLOWED_XCODES = frozenset({"259", "252"})

# 상품 상세 URL 패턴 — `/shop/shopdetail.html?branduid=<id>&xcode=<cat>&...`
_PRODUCT_LINK_PATTERN = re.compile(
    r"/shop/shopdetail\.html\?[^\"'\s]*branduid=\d+[^\"'\s]*"
)

# 상품명 안 괄호 안의 제조사 modelCode 추출 패턴 — `(JS4243)`, `(IO8217-008)`, `(P1GA262664)` 등.
# 영문 대문자 시작 강제 — 한국어 in 괄호 (예: "전용쌕") + 순수 숫자 (예: Puma 의 "10830302" — 사이트 내부 코드와 구분 어려움) 차단.
# 길이 3~20자 — 너무 짧은 false positive 회피.
_MODEL_CODE_PATTERN = re.compile(r'\(([A-Z][A-Z0-9-]{2,19})\)')


class Crazy11Client(SourceClient):
    """crazy11 HTTP 호출 client. 1 req/sec rate limit + UA + 차단 xcode 가드.

    EUC-KR 인코딩 사이트 — ``fetch_product`` 에서 ``response.encoding = 'euc-kr'`` 명시.
    """

    name: ClassVar[str] = "crazy11"
    base_url: ClassVar[str] = CRAZY11_BASE

    def __init__(
        self,
        rate_limit_per_sec: float = 1.0,
        user_agent: str = DEFAULT_USER_AGENT,
        timeout: float = 10.0,
    ) -> None:
        self._min_interval = 1.0 / rate_limit_per_sec if rate_limit_per_sec > 0 else 0.0
        self._last_request_at: float = 0.0
        self._session = requests.Session()
        self._session.headers.update({"User-Agent": user_agent})
        self._timeout = timeout

    # === SourceClient 인터페이스 ===

    def discover_product_urls(self, category: Category, limit: int) -> list[str]:
        """카테고리 xcode 별 페이지 fetch → ``shopdetail.html?branduid=...`` link 추출."""
        xcodes = _CATEGORY_XCODES.get(category)
        if not xcodes:
            raise ValueError(f"crazy11 에 매핑된 xcode 가 없는 카테고리: {category}")

        seen: set[str] = set()
        product_urls: list[str] = []
        for xcode in xcodes:
            if len(product_urls) >= limit:
                break
            cat_url = f"{CRAZY11_BASE}/shop/shopbrand.html?xcode={xcode}&type=Y"
            response = self._get(cat_url)
            response.raise_for_status()
            response.encoding = "euc-kr"
            for match in _PRODUCT_LINK_PATTERN.finditer(response.text):
                raw_path = match.group(0)
                # 상대 경로 → 절대 URL
                url = f"{CRAZY11_BASE}{raw_path}" if raw_path.startswith("/") else raw_path
                # URL 의 xcode 후처리 필터 — 카테고리 매핑된 xcode 와 일치만 채택.
                # 카테고리 페이지의 사이드/추천 영역 link (다른 카테고리 상품) 차단.
                # PR #90 PoC 의 카테고리 오염 50% fix.
                url_xcode = _extract_xcode(url)
                if url_xcode not in xcodes:
                    continue
                # branduid 기반 중복 제거 (xcode 다른 같은 상품 재등장 회피)
                branduid = _extract_branduid(url)
                if not branduid or branduid in seen:
                    continue
                seen.add(branduid)
                product_urls.append(url)
                if len(product_urls) >= limit:
                    break

        LOGGER.info(
            "[%s] discover %s — xcode=%s, urls=%d (limit=%d)",
            self.name, category.value, list(xcodes), len(product_urls), limit,
        )
        return product_urls

    def fetch_product(self, url: str) -> requests.Response:
        """EUC-KR 디코딩 강제."""
        response = self._get(url)
        response.encoding = "euc-kr"
        return response

    def parse_product(
        self, response: requests.Response, source_url: str
    ) -> RawProduct:
        # response.text 가 이미 EUC-KR decode 된 str — BeautifulSoup 은 from_encoding 미사용.
        soup = BeautifulSoup(response.text, "lxml")
        jsonld = _extract_jsonld_product(soup)

        # JSON-LD 의 name 이 한글 풀네임 — 가장 권위 있는 source
        name_ko = (jsonld or {}).get("name") or _extract_title(soup)
        # 제조사 modelCode 는 상품명 안 괄호 (예: '(JS4243)', '(IO8217-008)').
        # JSON-LD sku 는 사이트 내부 SKU (예: '001005003335') 라 신뢰 X — fallback 안 함.
        # 운영자 검수 신호로 추출 실패 시 None.
        style_code = _extract_model_code_from_name(name_ko)
        # image 는 첫 번째 array element
        image_url = _first_image(jsonld)

        # brand 는 JSON-LD 의 brand.name 이 신뢰 X (예: "38%" — 할인율).
        # None 으로 반환 → normalizer.match_brand 가 brands.yaml 한국어 alias 매칭으로 보강.
        brand: str | None = None

        return RawProduct(
            name=name_ko,         # 영문 og:title 미보유 시 한글로 fallback
            name_ko=name_ko,
            name_en=None,         # ADR-023 §D4 — crazy11 영문명 단일 source 부재
            brand=brand,
            style_code=style_code,
            release_date=None,    # crazy11 페이지에 일관된 release_date 표기 없음
            image_url=image_url,
            category_path=(jsonld or {}).get("category"),
            source_url=source_url,
        )

    # === 내부 HTTP 가드 ===

    def _get(self, url: str) -> requests.Response:
        self._guard_disallowed_xcode(url)
        self._wait_for_rate_limit()
        LOGGER.debug("GET %s", url)
        response = self._session.get(url, timeout=self._timeout)
        self._guard_blocked_status(response)
        return response

    def _guard_disallowed_xcode(self, url: str) -> None:
        """robots.txt 차단 xcode (259, 252) 의 URL 거부."""
        parsed = urlparse(url)
        qs = parse_qs(parsed.query)
        xcode = (qs.get("xcode") or [None])[0]
        if xcode and xcode in _DISALLOWED_XCODES:
            raise ForbiddenPathError(
                f"robots.txt 가 차단한 xcode 접근 시도: xcode={xcode}"
            )

    def _wait_for_rate_limit(self) -> None:
        elapsed = time.monotonic() - self._last_request_at
        if elapsed < self._min_interval:
            time.sleep(self._min_interval - elapsed)
        self._last_request_at = time.monotonic()

    def _guard_blocked_status(self, response: requests.Response) -> None:
        if response.status_code in (403, 429):
            raise CrawlerBlockedError(
                f"차단 응답 (status={response.status_code}). 운영자 확인 필요. "
                f"url={response.url}"
            )


# ===== 보조 함수 =====


def _extract_branduid(url: str) -> str | None:
    """상품 URL 에서 branduid 추출 — 중복 제거 키."""
    parsed = urlparse(url)
    qs = parse_qs(parsed.query)
    values = qs.get("branduid") or []
    return values[0] if values else None


def _extract_xcode(url: str) -> str | None:
    """URL query string 의 xcode 파라미터 추출 — 카테고리 필터 키."""
    parsed = urlparse(url)
    qs = parse_qs(parsed.query)
    values = qs.get("xcode") or []
    return values[0] if values else None


def _extract_model_code_from_name(name: str | None) -> str | None:
    """상품명 안 괄호 안의 제조사 modelCode 추출.

    crazy11 의 JSON-LD ``Product.sku`` 는 사이트 내부 SKU (예: '001005003335') 라
    제조사 모델코드가 아님 — 신뢰 X. 진짜 제조사 코드는 상품명 안 괄호
    (예: 'JS4243', 'IO8217-008', 'P1GA262664').

    패턴: 영문 대문자 시작 + 영문/숫자/하이픈 + 3~20자.
    - 한국어 in 괄호 ('전용쌕', '국내이월') 차단 (영문 시작 강제).
    - 순수 숫자 in 괄호 (Puma 의 '10830302' 같은 모델코드) 도 차단 — 사이트 내부 SKU 와 구분 어려움.
      운영자 검수 또는 후속 PR 에서 brand 별 분기 처리.

    PR #91 PoC 발견: BOOTS 125건 중 79건 (63%) 이 사이트 SKU 로 잘못 들어감 — 본 fix 로 차단.
    """
    if not name:
        return None
    match = _MODEL_CODE_PATTERN.search(name)
    return match.group(1) if match else None


def _extract_jsonld_product(soup: BeautifulSoup) -> dict | None:
    """``<script type="application/ld+json">`` 중 ``@type=Product`` 추출."""
    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        text = script.string or script.get_text() or ""
        if not text.strip():
            continue
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            continue
        candidates = data if isinstance(data, list) else [data]
        for entry in candidates:
            if isinstance(entry, dict) and entry.get("@type") == "Product":
                return entry
    return None


def _extract_title(soup: BeautifulSoup) -> str | None:
    """JSON-LD 부재 시 ``<title>`` fallback."""
    title = soup.find("title")
    if not title:
        return None
    text = title.get_text(strip=True)
    return text or None


def _first_image(jsonld: dict | None) -> str | None:
    """JSON-LD ``image`` (array 또는 string) 의 첫 URL."""
    if not jsonld:
        return None
    image = jsonld.get("image")
    if isinstance(image, list) and image:
        return image[0]
    if isinstance(image, str):
        return image
    return None
