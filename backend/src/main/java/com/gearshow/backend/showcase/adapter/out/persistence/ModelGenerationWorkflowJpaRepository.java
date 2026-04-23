package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelGenerationWorkflowJpaRepository
        extends JpaRepository<ModelGenerationWorkflowJpaEntity, Long> {

    Optional<ModelGenerationWorkflowJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
