package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.Showcase;
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

    // ── 공개 목록 조회 (최신순) ──

    /**
     * 첫 페이지 쇼케이스 목록을 조회한다 (ACTIVE, 최신순).
     *
     * @param size 조회 개수
     */
    List<Showcase> findAllFirstPage(int size);

    /**
     * 커서 기반 쇼케이스 목록을 조회한다 (createdAt DESC, id DESC).
     */
    List<Showcase> findAllWithCursor(Instant cursorCreatedAt, Long cursorId, int size);

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

    /**
     * 쇼케이스의 has3dModel 플래그만 업데이트한다.
     * 다른 필드를 건드리지 않아 동시 수정 시 lost update를 방지한다.
     */
    void updateHas3dModel(Long showcaseId, boolean has3dModel);
}
