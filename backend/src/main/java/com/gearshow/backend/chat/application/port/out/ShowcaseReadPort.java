package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.application.dto.ShowcaseSummary;

import java.util.List;
import java.util.Map;

/**
 * 쇼케이스 요약을 chat BC로 가져오기 위한 읽기 전용 포트.
 *
 * <p>구현 어댑터는 showcase BC의 공개 Application Service/Result를 감싸는 방식으로
 * 제공한다. chat 패키지는 showcase 도메인 타입을 절대 import하지 않는다.</p>
 */
public interface ShowcaseReadPort {

    ShowcaseSummary getSummary(Long showcaseId);

    /**
     * 복수 쇼케이스를 한 번에 조회한다 (채팅방 목록 N+1 회피).
     *
     * @return showcaseId → summary 매핑 (존재하지 않는 ID는 key에서 생략)
     */
    Map<Long, ShowcaseSummary> getSummaries(List<Long> showcaseIds);
}
