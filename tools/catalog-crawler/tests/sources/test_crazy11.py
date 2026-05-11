"""crazy11 출처 단위 테스트 (ADR-023).

EUC-KR 인코딩, JSON-LD parse, robots.txt 차단 xcode 거부, fullNameEn None 정책 등.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from catalog_crawler.sources import Category, ForbiddenPathError, SourceClient
from catalog_crawler.sources.crazy11 import (
    Crazy11Client,
    _CATEGORY_XCODES,
    _DISALLOWED_XCODES,
    _extract_branduid,
    _extract_model_code_from_name,
    _extract_xcode,
)


FIXTURE_PATH = (
    Path(__file__).parent.parent / "fixtures" / "sample_crazy11_product.html"
)


class _FakeResponse:
    """requests.Response 흉내 — text 디코딩 결과만 제공."""

    def __init__(self, content: bytes, encoding: str = "euc-kr", status_code: int = 200):
        self.content = content
        self.encoding = encoding
        self.status_code = status_code

    @property
    def text(self) -> str:
        return self.content.decode(self.encoding, errors="replace")


class TestCrazy11ClientInterface:
    def test_implements_source_client_abc(self):
        client = Crazy11Client(rate_limit_per_sec=10.0)
        assert isinstance(client, SourceClient)
        assert client.name == "crazy11"
        assert client.base_url.startswith("http://www.crazy11.co.kr")


class TestCategoryMapping:
    """ADR-023 카테고리 매핑은 메인 nav (홈페이지 상단 탭) 기준.

    시즌오프/베스트는 별도 view 옵션이라 매핑에서 제외 — 같은 상품 중복 + 사이드 link 오염 위험.
    """

    def test_boots_mapped_to_main_nav_xcodes_only(self):
        """BOOTS 는 메인 nav 의 축구화(257) + 풋살화(243) 2개 만."""
        assert _CATEGORY_XCODES[Category.BOOTS] == ("257", "243")

    def test_uniform_mapped_to_main_nav_xcodes_only(self):
        """UNIFORM 은 메인 nav 의 단체/유니폼(175) + 프리미어 리그(292) 만."""
        assert _CATEGORY_XCODES[Category.UNIFORM] == ("175", "292")

    def test_seasonoff_and_bestseller_excluded(self):
        """시즌오프(211, 138) / 베스트셀러(232, 237) view 옵션은 매핑에서 제외."""
        boots = _CATEGORY_XCODES[Category.BOOTS]
        for excluded in ("001", "002", "138", "211", "232", "237"):
            assert excluded not in boots, f"제외돼야 할 view xcode 가 매핑됨: {excluded}"

    def test_disallowed_xcodes_not_in_any_category(self):
        """robots.txt 가 차단한 xcode 가 어느 카테고리 매핑에도 포함되지 않아야 함."""
        all_mapped = set()
        for xcodes in _CATEGORY_XCODES.values():
            all_mapped.update(xcodes)
        for disallowed in _DISALLOWED_XCODES:
            assert disallowed not in all_mapped, f"차단 xcode 가 매핑됨: {disallowed}"


class TestRobotsTxtGuard:
    def test_blocked_xcode_raises_forbidden_path_error(self):
        client = Crazy11Client(rate_limit_per_sec=10.0)
        url = "http://www.crazy11.co.kr/shop/shopbrand.html?xcode=259&type=Y"
        with pytest.raises(ForbiddenPathError, match="xcode=259"):
            client._guard_disallowed_xcode(url)

    def test_allowed_xcode_passes_guard(self):
        client = Crazy11Client(rate_limit_per_sec=10.0)
        url = "http://www.crazy11.co.kr/shop/shopbrand.html?xcode=257&type=Y"
        # 예외 없음 = OK
        client._guard_disallowed_xcode(url)


class TestParseProduct:
    """JSON-LD Product schema 추출 검증 — 실제 사이트 fixture HTML 사용."""

    def setup_method(self):
        self.client = Crazy11Client(rate_limit_per_sec=10.0)
        self.response = _FakeResponse(FIXTURE_PATH.read_bytes())

    def test_extracts_korean_name_from_json_ld(self):
        """JSON-LD Product.name 이 한글 풀네임 — 가장 권위 있는 source."""
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["name_ko"] is not None
        assert "스타킹" in raw["name_ko"] or "UT704" in raw["name_ko"]

    def test_style_code_from_product_name_bracket_not_jsonld_sku(self):
        """ADR-023 후속 fix: style_code 는 상품명 안 괄호의 제조사 코드 추출 — JSON-LD sku 신뢰 X.

        fixture 의 상품명: '언더테크 액티브 쿨 튜브 스타킹 UT704 [파랑] #'
        - JSON-LD sku = '069005000037' (사이트 내부 SKU — 사용 안 함)
        - 상품명에 영문 시작 괄호 코드 없음 (UT704 는 괄호 없이 노출) → None
        """
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        # fixture 의 상품명에 `(...)` 안 영문 코드 없음 → style_code None
        # 사이트 SKU 가 fallback 으로 들어오면 회귀 — 명시적 None 검증.
        assert raw["style_code"] is None, (
            f"fixture 의 상품명에 괄호 안 영문 코드 없음 — None 이어야 하나 {raw['style_code']!r} 추출됨. "
            "JSON-LD sku fallback 회귀 의심."
        )

    def test_extracts_image_url_from_jsonld(self):
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["image_url"] is not None
        assert raw["image_url"].startswith("http")

    def test_returns_none_english_name_adr_023_d4(self):
        """ADR-023 §D4: crazy11 의 영문명 단일 source 부재 → name_en=None."""
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["name_en"] is None

    def test_returns_none_brand_jsonld_unreliable(self):
        """JSON-LD 의 brand.name 이 사이트의 할인율 등 잘못된 값으로 채워짐 → None."""
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        # 정상 brand 이름 ('Nike', 'Adidas' 등) 이 아니거나 None
        assert raw["brand"] is None

    def test_preserves_source_url(self):
        url = "http://www.crazy11.co.kr/shop/shopdetail.html?branduid=773971"
        raw = self.client.parse_product(self.response, url)
        assert raw["source_url"] == url


class TestExtractBranduid:
    def test_extracts_branduid_from_query(self):
        url = "http://www.crazy11.co.kr/shop/shopdetail.html?branduid=773971&xcode=127"
        assert _extract_branduid(url) == "773971"

    def test_returns_none_when_branduid_absent(self):
        url = "http://www.crazy11.co.kr/shop/shopbrand.html?xcode=257"
        assert _extract_branduid(url) is None


class TestExtractXcode:
    """xcode 추출 — discover 시점의 URL 후처리 필터 키 (PR #90 PoC 오염 fix)."""

    def test_extracts_xcode_from_query_string(self):
        url = "http://www.crazy11.co.kr/shop/shopdetail.html?branduid=773971&xcode=257"
        assert _extract_xcode(url) == "257"

    def test_returns_none_when_xcode_absent(self):
        url = "http://www.crazy11.co.kr/shop/page.html"
        assert _extract_xcode(url) is None


class TestExtractModelCodeFromName:
    """ADR-023 후속 fix: 상품명 안 괄호의 제조사 modelCode 추출.

    crazy11 의 JSON-LD ``Product.sku`` 는 사이트 내부 SKU — 진짜 제조사 코드는 상품명 안 괄호.
    PR #91 PoC 에서 BOOTS 79/125 (63%) 가 사이트 SKU 로 잘못 들어간 발견의 후속 fix.
    """

    @pytest.mark.parametrize(
        "name, expected",
        [
            ("아디다스 코파 퓨어 4 엘리트 FG (JS4243) 전용쌕/주걱/양말 #", "JS4243"),
            ("나이키 팬텀 VI 로우 엘리트 LV8 FG (IO8217-008) 전용쌕/주걱/양말 #", "IO8217-008"),
            ("미즈노 모나르시다 네오 셀렉트 III AG 와이드 (P1GA262664) #", "P1GA262664"),
            ("아디다스 프레데터 엘리트 FT FG (JS0378) 전용쌕/주걱/양말 #", "JS0378"),
        ],
    )
    def test_extracts_manufacturer_model_code(self, name, expected):
        assert _extract_model_code_from_name(name) == expected

    @pytest.mark.parametrize(
        "name",
        [
            "푸마 킹 얼티메이트 FG/AG (10830302) 전용쌕/주걱 #",   # 순수 숫자 — 사이트 내부 SKU 와 구분 어려움
            "상품명 (전용쌕)",                                       # 한국어 in 괄호
            "상품명 (국내이월)",                                     # 한국어 in 괄호
            "상품명만 있음",                                         # 괄호 없음
            "",                                                       # 빈 문자열
        ],
    )
    def test_returns_none_for_unreliable_or_missing_code(self, name):
        """순수 숫자 / 한국어 in 괄호 / 괄호 없음 → None — 운영자 검수 신호."""
        assert _extract_model_code_from_name(name) is None

    def test_returns_none_for_none_input(self):
        assert _extract_model_code_from_name(None) is None

    def test_extracts_first_match_when_multiple_brackets(self):
        """여러 괄호 중 첫 번째 영문 시작 매칭 채택."""
        name = "상품명 (JS4243) 추가 (다른괄호) (KR0001)"
        assert _extract_model_code_from_name(name) == "JS4243"


class TestDiscoverUrlFilter:
    """ADR-023 §D1 후속 — discover 시 URL 의 xcode 후처리 필터 검증.

    카테고리 페이지의 사이드/추천 영역 link (다른 카테고리 상품) 차단.
    """

    def test_filter_rejects_sidebar_link_with_other_xcode(self, mocker):
        """xcode=001 같은 매핑 외 link 는 discover 결과에서 제외 (BOOTS=257/243 만)."""
        client = Crazy11Client(rate_limit_per_sec=10.0)

        # carrier HTML — 메인 카테고리 link 1개 + 사이드 link 2개 (다른 카테고리)
        fake_html = """
        <a href="/shop/shopdetail.html?branduid=100&xcode=257&type=Y">메인 축구화</a>
        <a href="/shop/shopdetail.html?branduid=200&xcode=001&type=Y">사이드 (001, 매핑 외)</a>
        <a href="/shop/shopdetail.html?branduid=300&xcode=127&type=Y">사이드 (127 용품)</a>
        """

        class _Resp:
            text = fake_html
            encoding = "euc-kr"

            def raise_for_status(self):
                pass

        mocker.patch.object(client, "_get", return_value=_Resp())

        urls = client.discover_product_urls(Category.BOOTS, limit=10)

        # 매핑된 xcode (257, 243) 의 link 만 채택. 001/127 link 는 제외.
        assert any("branduid=100" in u for u in urls), "메인 카테고리 link 누락"
        assert not any("branduid=200" in u for u in urls), "사이드 xcode=001 link 가 차단되지 않음"
        assert not any("branduid=300" in u for u in urls), "사이드 xcode=127 link 가 차단되지 않음"


class TestEncodingHandling:
    """EUC-KR 인코딩 round-trip 검증 (ADR-023 §D5)."""

    def test_fixture_is_euc_kr_encoded(self):
        """fixture 자체가 EUC-KR 인지 — UTF-8 decode 시 깨짐 확인."""
        raw_bytes = FIXTURE_PATH.read_bytes()
        # EUC-KR 디코딩은 정상
        euc_kr_text = raw_bytes.decode("euc-kr", errors="strict")
        assert "축구화" in euc_kr_text or "크레이지11" in euc_kr_text

    def test_parse_handles_euc_kr_response_text(self):
        """response.text 가 이미 EUC-KR 로 decode 된 str — 한글 정상 추출."""
        client = Crazy11Client(rate_limit_per_sec=10.0)
        response = _FakeResponse(FIXTURE_PATH.read_bytes())
        raw = client.parse_product(response, "http://www.crazy11.co.kr/x")
        # 한글 풀네임이 정상 decode 됨
        assert raw["name_ko"] is not None
        # ASCII 만 있으면 디코딩 깨진 신호
        assert not raw["name_ko"].isascii()
