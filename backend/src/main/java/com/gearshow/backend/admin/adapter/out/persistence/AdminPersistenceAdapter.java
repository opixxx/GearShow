package com.gearshow.backend.admin.adapter.out.persistence;

import com.gearshow.backend.admin.application.port.out.AdminPort;
import com.gearshow.backend.admin.domain.model.Admin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminPersistenceAdapter implements AdminPort {

    private final AdminJpaRepository repository;
    private final AdminMapper mapper;

    @Override
    public Admin save(Admin admin) {
        AdminJpaEntity saved = repository.save(mapper.toJpaEntity(admin));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Admin> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<Admin> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }
}
