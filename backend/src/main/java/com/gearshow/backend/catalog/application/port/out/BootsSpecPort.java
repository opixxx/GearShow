package com.gearshow.backend.catalog.application.port.out;

import com.gearshow.backend.catalog.domain.model.BootsSpec;

import java.util.Optional;

/**
 * 축구화 스펙 Outbound Port.
 */
public interface BootsSpecPort {

    BootsSpec save(BootsSpec bootsSpec);

    Optional<BootsSpec> findByCatalogItemId(Long catalogItemId);
}
