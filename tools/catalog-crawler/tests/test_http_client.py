"""HTTP client 단위 테스트 — rate limit, 금지 경로, 차단 응답."""

from __future__ import annotations

import time

import pytest
import requests_mock

from catalog_crawler.http_client import (
    CrawlerBlockedError,
    ForbiddenPathError,
    KreamClient,
)


def test_disallowed_path_my_blocked():
    client = KreamClient()
    with pytest.raises(ForbiddenPathError):
        client.get("https://kream.co.kr/my/profile")


def test_disallowed_path_history_blocked():
    client = KreamClient()
    with pytest.raises(ForbiddenPathError):
        client.get("https://kream.co.kr/history/orders")


def test_403_raises_blocked():
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get("https://kream.co.kr/products/123", status_code=403)
        with pytest.raises(CrawlerBlockedError):
            client.get("https://kream.co.kr/products/123")


def test_429_raises_blocked():
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get("https://kream.co.kr/products/123", status_code=429)
        with pytest.raises(CrawlerBlockedError):
            client.get("https://kream.co.kr/products/123")


def test_rate_limit_enforced():
    client = KreamClient(rate_limit_per_sec=2.0)  # 0.5s 간격
    with requests_mock.Mocker() as m:
        m.get("https://kream.co.kr/products/1", text="ok")
        m.get("https://kream.co.kr/products/2", text="ok")
        start = time.monotonic()
        client.get("https://kream.co.kr/products/1")
        client.get("https://kream.co.kr/products/2")
        elapsed = time.monotonic() - start
    assert elapsed >= 0.5, f"두 요청 사이 0.5s 간격이 강제되어야 함 (실제 {elapsed:.3f}s)"


def test_user_agent_set():
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get("https://kream.co.kr/", text="ok")
        client.get("https://kream.co.kr/")
        history = m.request_history
        assert "GearShow-Catalog-Bot" in history[0].headers.get("User-Agent", "")
