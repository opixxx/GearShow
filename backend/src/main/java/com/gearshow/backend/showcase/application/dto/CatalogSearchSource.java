package com.gearshow.backend.showcase.application.dto;

/**
 * 검색 텍스트 합성 source — catalog 의 한국어/영문 풀네임 + brand + spec 한국어 alias 평탄화 (ADR-018).
 *
 * <p>showcase BC 가 catalog 도메인 객체 ({@code CatalogItem}, {@code BootsSpec}, {@code UniformSpec})
 * 를 직접 알지 않도록 application 레벨 read model 로 노출. BOOTS 카탈로그는 {@code siloNameKo}
 * 채워지고 {@code clubNameKo} 는 null, UNIFORM 은 반대.</p>
 *
 * @param fullNameKo  한국어 풀네임 (PR #75 ADR-016)
 * @param fullNameEn  영문 풀네임 (PR #75 ADR-016)
 * @param brand       브랜드 canonical (영문)
 * @param siloNameKo  사일로 한국어 alias (BOOTS, nullable)
 * @param clubNameKo  클럽/국가대표 한국어 alias (UNIFORM, nullable)
 */
public record CatalogSearchSource(
        String fullNameKo,
        String fullNameEn,
        String brand,
        String siloNameKo,
        String clubNameKo
) {
}
