package com.gearshow.backend.chat.application.dto;

/**
 * 쇼케이스 요약 정보 (ShowcaseReadPort 결과).
 *
 * <p>chat BC가 showcase BC로부터 받는 읽기 전용 스냅샷. 도메인 모델은 노출되지 않는다.</p>
 *
 * @param showcaseId   쇼케이스 ID
 * @param sellerId     쇼케이스 소유자(= 판매자) ID
 * @param title        제목
 * @param thumbnailUrl 대표 이미지 URL (null 가능)
 * @param chatStartable 현재 상태에서 신규 채팅방 생성이 허용되는지
 */
public record ShowcaseSummary(
        Long showcaseId,
        Long sellerId,
        String title,
        String thumbnailUrl,
        boolean chatStartable
) {
}
