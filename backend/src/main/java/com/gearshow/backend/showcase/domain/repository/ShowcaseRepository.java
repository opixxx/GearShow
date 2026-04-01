package com.gearshow.backend.showcase.domain.repository;

import com.gearshow.backend.showcase.domain.model.Showcase;

import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 도메인 저장소 인터페이스.
 */
public interface ShowcaseRepository {

    /**
     * 쇼케이스를 저장한다.
     *
     * @param showcase 저장할 쇼케이스
     * @return 저장된 쇼케이스
     */
    Showcase save(Showcase showcase);

    /**
     * ID로 쇼케이스를 조회한다.
     *
     * @param id 쇼케이스 ID
     * @return 쇼케이스 Optional
     */
    Optional<Showcase> findById(Long id);

    /**
     * 소유자 ID로 쇼케이스 목록을 조회한다.
     *
     * @param ownerId 소유자 ID
     * @return 쇼케이스 목록
     */
    List<Showcase> findByOwnerId(Long ownerId);
}
