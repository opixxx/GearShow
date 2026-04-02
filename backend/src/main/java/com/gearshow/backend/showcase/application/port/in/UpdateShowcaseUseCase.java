package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;

/**
 * 쇼케이스 수정 유스케이스.
 */
public interface UpdateShowcaseUseCase {

    /**
     * 쇼케이스를 수정한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param ownerId    요청자 ID (소유자 검증용)
     * @param command    수정 커맨드
     */
    void update(Long showcaseId, Long ownerId, UpdateShowcaseCommand command);
}
