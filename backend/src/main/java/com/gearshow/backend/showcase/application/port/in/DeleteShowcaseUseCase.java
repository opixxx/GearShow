package com.gearshow.backend.showcase.application.port.in;

/**
 * 쇼케이스 삭제 유스케이스.
 */
public interface DeleteShowcaseUseCase {

    /**
     * 쇼케이스를 삭제한다 (소프트 삭제).
     *
     * @param showcaseId 쇼케이스 ID
     * @param ownerId    요청자 ID (소유자 검증용)
     */
    void delete(Long showcaseId, Long ownerId);
}
