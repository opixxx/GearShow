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
     * 복수 쇼케이스 조회.
     *
     * <p><b>현재 구현 한계</b>: showcase BC가 lightweight summary batch UseCase를 제공하지 않아
     * 어댑터에서 N회 개별 호출 + 각 호출은 detail-heavy 그래프를 로드한다. 채팅방 목록 페이지 크기(≤100)
     * 안에서 동작하지만 진정한 N+1 회피는 아니다. Phase 후속 작업에서 showcase BC에
     * {@code GetShowcaseSummariesUseCase} 추가 예정.</p>
     *
     * @return showcaseId → summary 매핑 (존재하지 않는 ID는 key에서 생략)
     */
    Map<Long, ShowcaseSummary> getSummaries(List<Long> showcaseIds);
}
