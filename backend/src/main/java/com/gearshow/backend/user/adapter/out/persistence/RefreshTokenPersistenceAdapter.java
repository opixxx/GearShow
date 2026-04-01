package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.application.port.out.RefreshTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Refresh Token Persistence Adapter.
 * RefreshTokenPort를 구현하여 JPA를 통해 Refresh Token을 관리한다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public void save(Long userId, String token, LocalDateTime expiresAt) {
        RefreshTokenJpaEntity entity = RefreshTokenJpaEntity.builder()
                .userId(userId)
                .token(token)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenJpaRepository.save(entity);
    }

    @Override
    public Optional<Long> findUserIdByToken(String token) {
        return refreshTokenJpaRepository.findByToken(token)
                .filter(entity -> entity.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(RefreshTokenJpaEntity::getUserId);
    }

    @Override
    public void deleteByUserId(Long userId) {
        refreshTokenJpaRepository.deleteByUserId(userId);
    }
}
