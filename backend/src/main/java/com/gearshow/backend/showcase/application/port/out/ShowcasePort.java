package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 Outbound Port.
 */
public interface ShowcasePort {

    Showcase save(Showcase showcase);

    Optional<Showcase> findById(Long id);

    // ── 공개 목록 조회 ──

    /**
     * 첫 페이지 쇼케이스 목록을 조회한다.
     *
     * @param size            조회 개수
     * @param catalogItemIds  카탈로그 ID 필터 (null이면 전체)
     * @param keyword         제목 검색 (null이면 전체)
     * @param isForSale       판매 여부 필터 (null이면 전체)
     * @param conditionGrade  상태 등급 필터 (null이면 전체)
     */
    List<Showcase> findAllFirstPage(int size, List<Long> catalogItemIds,
                                    String keyword, Boolean isForSale,
                                    ConditionGrade conditionGrade);

    /**
     * 커서 기반 쇼케이스 목록을 조회한다 (createdAt DESC, id DESC).
     */
    List<Showcase> findAllWithCursor(Instant cursorCreatedAt, Long cursorId, int size,
                                     List<Long> catalogItemIds, String keyword,
                                     Boolean isForSale, ConditionGrade conditionGrade);

    // ── 내 쇼케이스 목록 조회 ──

    /**
     * 소유자 기준 첫 페이지 쇼케이스 목록을 조회한다.
     */
    List<Showcase> findByOwnerIdFirstPage(Long ownerId, int size,
                                          ShowcaseStatus showcaseStatus);

    /**
     * 소유자 기준 커서 기반 쇼케이스 목록을 조회한다 (createdAt DESC, id DESC).
     */
    List<Showcase> findByOwnerIdWithCursor(Long ownerId, Instant cursorCreatedAt, Long cursorId,
                                           int size, ShowcaseStatus showcaseStatus);
}
