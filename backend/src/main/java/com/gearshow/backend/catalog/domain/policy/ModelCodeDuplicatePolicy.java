package com.gearshow.backend.catalog.domain.policy;

import com.gearshow.backend.catalog.domain.exception.DuplicateModelCodeException;
import com.gearshow.backend.catalog.domain.vo.Category;

/**
 * 카탈로그 아이템의 모델 코드 중복 검증 정책.
 * 동일 카테고리 내에서 모델 코드가 중복되면 안 된다.
 */
public class ModelCodeDuplicatePolicy {

    /**
     * 모델 코드 중복 여부를 검증한다.
     * 모델 코드가 null이거나 현재 코드와 동일하면 검사를 건너뛴다.
     *
     * @param category         카테고리
     * @param newModelCode     변경할 모델 코드
     * @param currentModelCode 기존 모델 코드 (신규 등록 시 null)
     * @param existsChecker    중복 존재 여부 확인 함수
     */
    public static void validate(Category category, String newModelCode,
                                String currentModelCode,
                                ModelCodeExistsChecker existsChecker) {
        if (newModelCode == null || newModelCode.equals(currentModelCode)) {
            return;
        }
        if (existsChecker.exists(category, newModelCode)) {
            throw new DuplicateModelCodeException();
        }
    }

    /**
     * 모델 코드 존재 여부를 확인하는 함수형 인터페이스.
     */
    @FunctionalInterface
    public interface ModelCodeExistsChecker {
        boolean exists(Category category, String modelCode);
    }
}
