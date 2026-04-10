package com.gearshow.backend.showcase.application.port.in;

/**
 * Tripo 생성 상태 폴링 유스케이스 (Inbound Port).
 *
 * <p>폴링 스케줄러가 주기적으로 호출한다. GENERATING 상태의 모델들에 대해
 * Tripo 상태를 확인하고, 완료/실패/타임아웃을 처리한다.</p>
 *
 * <p>이 유스케이스를 별도로 두는 이유는 스케줄러(Inbound Adapter) 와
 * 비즈니스 로직(Application Service) 를 명확히 분리하기 위함이다. 스케줄러는
 * "언제" 호출할지만 결정하고, "무엇을" 할지는 이 유스케이스에 위임한다.</p>
 */
public interface PollGenerationStatusUseCase {

    /**
     * 한 번의 폴링 사이클을 실행한다.
     *
     * @return 이번 호출에서 실제로 상태 변경(완료/실패)이 발생한 모델 수
     */
    int pollOnce();
}
