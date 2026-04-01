package com.gearshow.backend.user.adapter.out.persistence;

import com.gearshow.backend.user.domain.model.User;
import org.springframework.stereotype.Component;

/**
 * User 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class UserMapper {

    /**
     * 도메인 모델을 JPA 엔티티로 변환한다.
     *
     * @param user 도메인 모델
     * @return JPA 엔티티
     */
    public UserJpaEntity toJpaEntity(User user) {
        return UserJpaEntity.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .phoneNumber(user.getPhoneNumber())
                .phoneVerified(user.isPhoneVerified())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * JPA 엔티티를 도메인 모델로 변환한다.
     *
     * @param entity JPA 엔티티
     * @return 도메인 모델
     */
    public User toDomain(UserJpaEntity entity) {
        return User.builder()
                .id(entity.getId())
                .nickname(entity.getNickname())
                .profileImageUrl(entity.getProfileImageUrl())
                .phoneNumber(entity.getPhoneNumber())
                .phoneVerified(entity.isPhoneVerified())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
