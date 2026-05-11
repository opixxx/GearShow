"""다중 출처 catalog 크롤러 추상화 (ADR-023).

각 출처(`sources/<site>.py`) 는 ``SourceClient`` 를 상속하여 다음 책임을 가진다:

1. HTTP fetch — rate-limited, User-Agent 명시, robots.txt 차단 경로 거부
2. 카테고리별 상품 URL discover — sitemap 또는 search endpoint
3. 상품 상세 HTML parse → ``RawProduct``

운영 정책 (ADR-017, ADR-021 §D7, ADR-023):
- robots.txt 준수 — 차단 경로는 ``ForbiddenPathError`` 로 client 단 거부
- Anti-bot 우회 시도 금지 — 403/429 응답 시 ``CrawlerBlockedError`` 즉시 중단
- 외부 사이트 식별자(예: ``KreamClient``, ``Crazy11Client``) 는 의도적 유지
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from enum import Enum
from typing import ClassVar, TypedDict

import requests


class RawProduct(TypedDict, total=False):
    """출처별 parse 결과의 공통 형식 — Kream/crazy11 모두 동일.

    영문명이 단일 source 부재인 사이트(crazy11) 는 ``name_en=None`` 반환 (ADR-023 §D4).
    """

    name: str | None
    name_ko: str | None
    name_en: str | None
    brand: str | None
    style_code: str | None
    release_date: str | None
    image_url: str | None
    category_path: str | None
    source_url: str | None


class Category(str, Enum):
    """catalog 카테고리 — Kream/crazy11 등 모든 출처 공통."""

    BOOTS = "BOOTS"
    UNIFORM = "UNIFORM"


class ForbiddenPathError(ValueError):
    """robots.txt 가 차단한 경로 접근 시도 — client 단에서 거부."""


class CrawlerBlockedError(RuntimeError):
    """403/429 등 차단 응답 — 즉시 중단 (Anti-bot 우회 시도 금지 정책)."""


class SourceClient(ABC):
    """단일 출처(Kream / crazy11 등)의 크롤러 인터페이스.

    하위 클래스는 ``name`` / ``base_url`` ClassVar 와 세 추상 메서드를 구현한다.
    HTTP rate limit · UA · 차단 경로 가드 같은 공통 로직은 하위에서 구현하되,
    인코딩이나 메타데이터 형식 같은 사이트 별 차이는 자체 처리.
    """

    name: ClassVar[str]
    base_url: ClassVar[str]

    @abstractmethod
    def discover_product_urls(self, category: Category, limit: int) -> list[str]:
        """카테고리별 상품 상세 URL 후보 목록 추출.

        :param category: BOOTS / UNIFORM
        :param limit: 최대 상품 수 — 호출자가 제한. 초과 후보는 호출자가 잘라낸다.
        :returns: 상품 상세 URL list (중복 제거됨, 등장 순서 유지).
        :raises CrawlerBlockedError: 검색/sitemap fetch 시 차단 응답.
        """

    @abstractmethod
    def fetch_product(self, url: str) -> requests.Response:
        """상품 상세 HTML fetch — rate-limited, 인코딩은 사이트별 처리.

        :raises ForbiddenPathError: robots.txt 차단 경로.
        :raises CrawlerBlockedError: 403/429 차단 응답.
        """

    @abstractmethod
    def parse_product(self, response: requests.Response, source_url: str) -> "RawProduct":
        """fetch 응답을 ``RawProduct`` 로 파싱.

        사이트별 메타데이터 형식(Kream: ``<meta keywords>`` 3-token,
        crazy11: ``<title>`` + JSON-LD) 차이를 흡수.
        영문명이 단일 source 부재인 사이트는 ``name_en=None`` 반환 (ADR-023 §D4).
        """


__all__ = [
    "Category",
    "SourceClient",
    "RawProduct",
    "ForbiddenPathError",
    "CrawlerBlockedError",
]
