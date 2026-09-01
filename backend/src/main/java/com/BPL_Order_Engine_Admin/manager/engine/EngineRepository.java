package com.BPL_Order_Engine_Admin.manager.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EngineRepository extends JpaRepository<EngineEntity, UUID> {

    /**
     * Factory lookup. Excludes soft-deleted rows. The
     * {@code OrderEngineFactory} (in #17) uses this.
     */
    Optional<EngineEntity> findByCodeAndDeletedAtIsNull(String code);
}
