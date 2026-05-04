package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;
import com.gearshow.backend.showcase.domain.model.Showcase;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 검색 텍스트 합성 헬퍼 (ADR-018).
 *
 * <p>catalog 의 한국어 alias (있으면) + Showcase 의 직접 입력값 (brand, modelCode, title,
 * description) 을 공백 join 하여 검색 source 를 합성. 합성 정책 (ADR-018 §D3):</p>
 * <ul>
 *   <li>catalog 합집합 — 있으면 fullNameKo/En + brand + siloNameKo/clubNameKo 추가</li>
 *   <li>직접 입력값은 catalog 유무 무관 항상 포함 (catalog alias 가 부족해도 사용자 description 으로 매칭 가능)</li>
 *   <li>중복 제거 안 함 — LIKE 부분 문자열 매칭이라 무해</li>
 *   <li>null/blank 토큰은 제거</li>
 *   <li>1000자 초과 시 truncate — VARCHAR(1000) 제약 보호</li>
 * </ul>
 *
 * <p>순수 함수 — application 의 다른 service 또는 port 에 의존하지 않는다.</p>
 */
public final class SearchTextComposer {

    /** ADR-018 §D3: VARCHAR(1000) 제약. 평균 100~500자 예상, 1000자 마진 충분. */
    public static final int MAX_LENGTH = 1000;

    private SearchTextComposer() {
        throw new AssertionError("정적 헬퍼 — 인스턴스 생성 금지");
    }

    /**
     * Showcase 와 (선택적) catalog source 로부터 검색 텍스트를 합성한다.
     *
     * @param showcase 직접 입력값을 가진 Showcase (필수)
     * @param source   catalog 측 read model (catalogItemId 미설정 시 null)
     * @return 합성된 검색 텍스트. 모든 토큰이 null/blank 면 null 반환.
     */
    public static String compose(Showcase showcase, CatalogSearchSource source) {
        if (showcase == null) {
            throw new IllegalArgumentException("showcase 는 필수");
        }

        List<String> tokens = new ArrayList<>();
        if (source != null) {
            // catalog 있음: 한국어 풀네임 + 영문 풀네임 + brand + spec 한국어 alias
            tokens.add(source.fullNameKo());
            tokens.add(source.fullNameEn());
            tokens.add(source.brand());
            tokens.add(source.siloNameKo());
            tokens.add(source.clubNameKo());
        }
        // catalog 유무 무관: 사용자 직접 입력값
        tokens.add(showcase.getBrand());
        tokens.add(showcase.getModelCode());
        tokens.add(showcase.getTitle());
        tokens.add(showcase.getDescription());

        String joined = tokens.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));

        if (joined.isEmpty()) {
            return null;
        }
        if (joined.length() > MAX_LENGTH) {
            return joined.substring(0, MAX_LENGTH);
        }
        return joined;
    }
}
