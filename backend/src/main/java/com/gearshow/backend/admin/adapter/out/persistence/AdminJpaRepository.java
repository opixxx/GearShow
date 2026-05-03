package com.gearshow.backend.admin.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminJpaRepository extends JpaRepository<AdminJpaEntity, Long> {

    Optional<AdminJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
