package com.gearshow.backend.admin.adapter.out.persistence;

import com.gearshow.backend.admin.domain.model.Admin;
import org.springframework.stereotype.Component;

/**
 * Admin 도메인 ↔ JPA 엔티티 매퍼.
 */
@Component
public class AdminMapper {

    public AdminJpaEntity toJpaEntity(Admin admin) {
        return AdminJpaEntity.builder()
                .id(admin.getId())
                .email(admin.getEmail())
                .passwordHash(admin.getPasswordHash())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }

    public Admin toDomain(AdminJpaEntity entity) {
        return Admin.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .passwordHash(entity.getPasswordHash())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
