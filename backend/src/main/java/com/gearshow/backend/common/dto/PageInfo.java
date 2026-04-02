package com.gearshow.backend.common.dto;

import com.gearshow.backend.common.util.PageTokenUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 페이징 응답.
 *
 * @param pageToken 다음 페이지 토큰 (마지막 페이지이면 null)
 * @param data      조회된 데이터 목록
 * @param hasNext   다음 페이지 존재 여부
 */
public record PageInfo<T>(
        String pageToken,
        List<T> data,
        boolean hasNext
) {

    /**
     * 커서 페이징 응답을 생성한다.
     * 요청한 size보다 1개 더 조회하여 hasNext를 판단한다.
     *
     * @param data                    조회된 데이터 (size + 1개 조회됨)
     * @param expectedSize            요청한 페이지 크기
     * @param firstPageTokenFunction  pageToken 첫 번째 값 추출 함수
     * @param secondPageTokenFunction pageToken 두 번째 값 추출 함수
     * @return 커서 페이징 응답
     */
    public static <T> PageInfo<T> of(
            List<T> data,
            int expectedSize,
            Function<T, Object> firstPageTokenFunction,
            Function<T, Object> secondPageTokenFunction
    ) {
        if (data.size() <= expectedSize) {
            return new PageInfo<>(null, data, false);
        }

        T lastValue = data.get(expectedSize - 1);
        String pageToken = PageTokenUtil.encode(Pair.of(
                firstPageTokenFunction.apply(lastValue),
                secondPageTokenFunction.apply(lastValue)
        ));

        return new PageInfo<>(pageToken, data.subList(0, expectedSize), true);
    }
}
