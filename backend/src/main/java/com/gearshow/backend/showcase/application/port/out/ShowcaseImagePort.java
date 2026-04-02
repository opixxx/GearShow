package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ShowcaseImage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 쇼케이스 이미지 Outbound Port.
 */
public interface ShowcaseImagePort {

    /**
     * 쇼케이스 이미지를 저장한다.
     */
    ShowcaseImage save(ShowcaseImage image);

    /**
     * 쇼케이스 이미지를 일괄 저장한다.
     */
    List<ShowcaseImage> saveAll(List<ShowcaseImage> images);

    /**
     * ID로 이미지를 조회한다.
     */
    Optional<ShowcaseImage> findById(Long id);

    /**
     * 쇼케이스 ID로 이미지 목록을 조회한다.
     */
    List<ShowcaseImage> findByShowcaseId(Long showcaseId);

    /**
     * 이미지를 삭제한다.
     */
    void deleteById(Long id);

    /**
     * 쇼케이스 ID로 이미지 개수를 조회한다.
     */
    int countByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID로 대표 이미지 URL을 조회한다.
     */
    String findPrimaryImageUrlByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스의 대표 이미지 URL을 일괄 조회한다.
     *
     * @param showcaseIds 쇼케이스 ID 목록
     * @return showcaseId → primaryImageUrl 매핑
     */
    Map<Long, String> findPrimaryImageUrlsByShowcaseIds(List<Long> showcaseIds);
}
