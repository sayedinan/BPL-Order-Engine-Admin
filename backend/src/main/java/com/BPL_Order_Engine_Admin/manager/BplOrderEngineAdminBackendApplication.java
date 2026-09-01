package com.BPL_Order_Engine_Admin.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * v0.3 Spring Boot entrypoint for the BPL Order Engine Admin backend.
 *
 * <p>Component scan covers {@code com.BPL_Order_Engine_Admin.manager},
 * so the auth, user, engine, audit, config, and web sub-packages are
 * picked up automatically. Scheduling is enabled per-component
 * (the {@code MockEngineOperations} heartbeat uses
 * {@code @Scheduled(fixedDelay = 2000)}).
 */
@SpringBootApplication
public class BplOrderEngineAdminBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BplOrderEngineAdminBackendApplication.class, args);
    }
}
