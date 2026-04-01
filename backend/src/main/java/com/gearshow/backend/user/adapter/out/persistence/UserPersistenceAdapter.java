package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 사용자 Persistence Adapter.
 * UserPort를 구현하여 JPA를 통해 사용자 데이터를 관리한다.
 */
@Repository
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserPort {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        UserJpaEntity jpaEntity = userMapper.toJpaEntity(user);
        UserJpaEntity saved = userJpaRepository.save(jpaEntity);
        return userMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id)
                .map(userMapper::toDomain);
    }
}
