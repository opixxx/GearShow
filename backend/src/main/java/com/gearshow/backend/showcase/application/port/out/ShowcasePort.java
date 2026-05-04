package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ContentHash;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 Outbound Port.
 */
public interface ShowcasePort {

    Showcase save(Showcase showcase);

    Optional<Showcase> findById(Long id);

    /**
     * 복수 ID 로 쇼케이스를 일괄 조회한다. 존재하지 않는 ID 는 결과에서 누락된다.
     *
     * @param ids 쇼케이스 ID 컬렉션. 빈 컬렉션이면 빈 리스트 반환.
     * @return 조회된 쇼케이스 목록 (순서 보장 없음)
     */
    List<Showcase> findAllByIds(Collection<Long> ids);

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

    // ── 키워드 검색 (ADR-019) ──

    /**
     * 키워드 LIKE 매칭 첫 페이지 (ACTIVE, search_text NOT NULL, 최신순).
     *
     * <p>ADR-019 §D1: 대소문자 무시 (LOWER), search_text 합성 결과(catalog 한국어/영문 풀네임 +
     * spec 한국어 alias + 직접 입력값) 안에서 부분 문자열 매칭. backfill 안 된 기등록 행
     * (search_text IS NULL) 은 결과에서 제외된다.</p>
     */
    List<Showcase> findByKeywordFirstPage(String keyword, int size);

    /**
     * 키워드 LIKE 매칭 커서 페이징 (createdAt DESC, id DESC).
     */
    List<Showcase> findByKeywordWithCursor(String keyword, Instant cursorCreatedAt, Long cursorId, int size);

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
     *
     * <p><b>가드</b>: {@code DELETED} 상태 쇼케이스는 갱신하지 않는다. 도메인 정책상 삭제된
     * 쇼케이스에는 어떤 read-model 변경도 박지 않아야 한다.</p>
     *
     * <p><b>반환값</b>: 호출자가 정합성 검증에 활용하도록 affected 행 수를 반환한다.
     * affected=0 은 (a) 쇼케이스가 존재하지 않거나 (b) DELETED 상태임을 의미한다.</p>
     *
     * @return 갱신된 행 수 (정상 1, 미존재/DELETED 0)
     */
    int updateHas3dModel(Long showcaseId, boolean has3dModel);

    /**
     * 같은 소유자가 지정 시각 이후 같은 {@code contentHash} 로 생성한 가장 최근 쇼케이스를 조회한다.
     *
     * <p>ADR-011 ②: API 경계 Idempotency-Key 가 실패한 경우를 위한 내용 기반 fallback.
     * 인덱스 {@code idx_showcase_owner_content_created} 를 활용한다.</p>
     *
     * @param ownerId       소유자 ID
     * @param contentHash   이미지 조합 해시
     * @param createdAfter  조회 대상 시각 하한 (예: 현재 시각 - 10분)
     * @return 존재하면 해당 쇼케이스, 없으면 {@code Optional.empty()}
     */
    Optional<Showcase> findRecentByOwnerAndContentHash(Long ownerId, ContentHash contentHash,
                                                      Instant createdAfter);
}
