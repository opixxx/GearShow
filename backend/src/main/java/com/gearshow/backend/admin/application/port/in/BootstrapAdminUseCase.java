package com.gearshow.backend.admin.application.port.in;

/**
 * 첫 관리자 부트스트랩 유스케이스.
 *
 * <p>{@code ApplicationStartedEvent} 시점에 환경변수에 채워진 email/password 로
 * 첫 관리자를 INSERT 한다. 이미 같은 email 의 관리자가 존재하면 skip.</p>
 */
public interface BootstrapAdminUseCase {

    /**
     * 환경변수 기반 첫 관리자 부트스트랩을 1회 시도한다.
     *
     * <p>email/password 둘 중 하나라도 비어있거나 이미 같은 email 이 존재하면 아무 동작도 하지 않는다.</p>
     */
    void bootstrap();
}
