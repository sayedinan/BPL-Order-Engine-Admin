package com.enginehelm.engine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineRepository extends JpaRepository<Engine, Long> {
    boolean existsByName(String name);
}
