package com.gearshow.backend.admin.application.port.out;

import com.gearshow.backend.admin.domain.model.Admin;

import java.util.Optional;

/**
 * 관리자 영속성 포트.
 */
public interface AdminPort {

    /**
     * 관리자 계정을 저장한다.
     */
    Admin save(Admin admin);

    /**
     * email 로 관리자 계정을 조회한다.
     */
    Optional<Admin> findByEmail(String email);

    /**
     * email 의 관리자 계정 존재 여부를 확인한다.
     */
    boolean existsByEmail(String email);
}
