package com.gearshow.backend.showcase.domain.vo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 쇼케이스 이미지 조합의 결정적 SHA-256 해시 (hex 64자) VO.
 *
 * <p>같은 사용자가 짧은 시간 창 안에 동일한 이미지 조합으로 중복 등록하는 케이스를
 * application 레벨에서 감지하기 위한 비즈니스 식별자. ADR-011 ② 의 fallback 멱등성 수단이다.</p>
 *
 * <p>클라이언트가 계산하여 전달한다. 서버는 형식만 검증하고 저장한다.</p>
 */
public record ContentHash(String value) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public ContentHash {
        Objects.requireNonNull(value, "contentHash 값은 null 일 수 없습니다");
        if (!SHA256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "contentHash 는 소문자 SHA-256 hex 64자여야 합니다");
        }
    }

    /**
     * 정적 팩토리. 유효성 검증을 통과하면 값을 반환한다.
     */
    public static ContentHash of(String value) {
        return new ContentHash(value);
    }
}
