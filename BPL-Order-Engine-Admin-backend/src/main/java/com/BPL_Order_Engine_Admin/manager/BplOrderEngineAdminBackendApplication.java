package com.BPL_Order_Engine_Admin.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entrypoint for the BPL Order Engine Admin backend.
 *
 * <p>{@link EnableScheduling} turns on the {@code @Scheduled} task in
 * {@code BplOrderEngineOperations.heartbeat()} which synthesizes log
 * lines while the engine is in the {@code RUNNING} state. Component
 * scan covers the whole {@code com.BPL_Order_Engine_Admin.manager}
 * package, so the engine, web, and config sub-packages are picked up
 * automatically.
 */
@SpringBootApplication
@EnableScheduling
public class BplOrderEngineAdminBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BplOrderEngineAdminBackendApplication.class, args);
	}

}
