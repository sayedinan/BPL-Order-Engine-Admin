package com.BPL_Order_Engine_Admin.manager.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * v0.3 audit annotation (SPEC §7.2). Applied to controller methods
 * whose state changes need an audit row.
 *
 * <p>Usage:
 * <pre>
 * {@code
 * @Audited(action = AuditAction.START_ENGINE)
 * public void start(@PathVariable String code) { ... }
 * }
 * </pre>
 *
 * <p>The {@code AuditAspect} reads the action, extracts the actor
 * from the security context, optionally resolves the target engine
 * from the URI, evaluates the SpEL {@code details} expression, and
 * writes the row on both success and exception paths.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction action();

    /**
     * If true, the aspect extracts the engine code from the URI
     * ({@code /api/engines/{code}/...}) and looks up the
     * {@code Engine.code} for the {@code targetEngineCode} column.
     */
    boolean targetEngineFromPath() default false;

    /**
     * SpEL expression evaluated against the method args + return
     * value. Should return a {@code Map<String, Object>} that
     * becomes the {@code details} JSON. Empty string = write
     * {@code {}}.
     */
    String details() default "";
}
