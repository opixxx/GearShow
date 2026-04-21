package com.gearshow.backend.user.application.port.out;

import com.gearshow.backend.user.domain.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 Outbound Port.
 */
public interface UserPort {

    /**
     * 사용자를 저장한다.
     *
     * @param user 저장할 사용자
     * @return 저장된 사용자
     */
    User save(User user);

    /**
     * ID로 사용자를 조회한다.
     *
     * @param id 사용자 ID
     * @return 사용자 Optional
     */
    Optional<User> findById(Long id);

    /**
     * 복수 ID로 사용자를 일괄 조회한다. 존재하지 않는 ID 는 결과에서 누락된다.
     *
     * @param ids 사용자 ID 컬렉션. 빈 컬렉션이면 빈 리스트 반환.
     * @return 조회된 사용자 목록 (순서 보장 없음)
     */
    List<User> findAllByIds(Collection<Long> ids);

    /**
     * 닉네임 중복 여부를 확인한다.
     *
     * @param nickname 닉네임
     * @return 중복 여부
     */
    boolean existsByNickname(String nickname);
}
