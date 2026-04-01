package com.gearshow.backend.catalog.application.port.out;

import com.gearshow.backend.catalog.domain.model.UniformSpec;

import java.util.Optional;

/**
 * 유니폼 스펙 Outbound Port.
 */
public interface UniformSpecPort {

    UniformSpec save(UniformSpec uniformSpec);

    Optional<UniformSpec> findByCatalogItemId(Long catalogItemId);
}
