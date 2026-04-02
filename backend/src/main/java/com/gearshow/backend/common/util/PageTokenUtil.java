package com.gearshow.backend.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 커서 기반 페이징을 위한 PageToken 인코딩/디코딩 유틸리티.
 * 두 개의 값을 Base64로 인코딩하여 pageToken으로 사용한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PageTokenUtil {

    private static final String DELIMITER = "|";

    /**
     * 두 개의 값을 Base64 pageToken으로 인코딩한다.
     *
     * @param data 인코딩할 값 쌍
     * @return Base64 인코딩된 pageToken
     */
    public static <T, R> String encode(Pair<T, R> data) {
        String raw = toEncodableString(data.getLeft()) + DELIMITER + toEncodableString(data.getRight());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 값을 인코딩 가능한 문자열로 변환한다.
     * Instant는 epochMilli로 변환하여 decode와 일관성을 유지한다.
     */
    private static String toEncodableString(Object value) {
        if (value instanceof Instant instant) {
            return String.valueOf(instant.toEpochMilli());
        }
        return value.toString();
    }

    /**
     * pageToken을 디코딩하여 두 개의 값을 반환한다.
     *
     * @param pageToken  Base64 인코딩된 pageToken
     * @param firstType  첫 번째 값의 타입
     * @param secondType 두 번째 값의 타입
     * @return 디코딩된 값 쌍
     */
    public static <T, R> Pair<T, R> decode(String pageToken, Class<T> firstType, Class<R> secondType) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(pageToken), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 pageToken 형식입니다: Base64 디코딩 실패", e);
        }

        String[] parts = decoded.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("유효하지 않은 pageToken입니다");
        }

        return Pair.of(parseValue(parts[0], firstType), parseValue(parts[1], secondType));
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseValue(String data, Class<T> clazz) {
        if (clazz == String.class) {
            return (T) data;
        } else if (clazz == Long.class) {
            return (T) Long.valueOf(data);
        } else if (clazz == Integer.class) {
            return (T) Integer.valueOf(data);
        } else if (clazz == Instant.class) {
            return (T) Instant.ofEpochMilli(Long.parseLong(data));
        }

        throw new IllegalArgumentException("지원하지 않는 타입입니다: " + clazz);
    }
}
