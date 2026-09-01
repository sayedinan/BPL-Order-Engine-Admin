package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * v0.3 create-engine request (SPEC §4.5).
 *
 * <p>Code: {@code ^[A-Z0-9_]{2,16}$}, unique among non-deleted rows.
 * For {@code mode = REAL}, the scripts are sanity-checked (no
 * {@code ; rm}, {@code && rm}, {@code | rm}, backticks) but NOT
 * sandboxed.
 */
public record CreateEngineRequest(
    @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,16}$") String code,
    @NotBlank @Size(max = 80) String name,
    @NotNull EngineMode mode,
    @NotBlank @Size(max = 64) String serverIp,
    @NotBlank @Size(max = 64) String serverUsername,
    @NotBlank @Size(max = 512) String serverPassword,
    String startScript,
    String stopScript,
    String logScript
) {}
