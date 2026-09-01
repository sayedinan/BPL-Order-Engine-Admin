package com.BPL_Order_Engine_Admin.manager.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * v0.3 password strength constraint (SPEC §4.6.1 / API.md §1.4).
 *
 * <p>Rule:
 * <ul>
 *   <li>Min 12 chars, max 128 chars.</li>
 *   <li>At least one ASCII letter {@code [A-Za-z]}.</li>
 *   <li>At least one digit {@code [0-9]}.</li>
 *   <li>No whitespace-only, no control characters.</li>
 * </ul>
 *
 * <p>Message returned in the 422 envelope (per API.md §5).
 */
@Documented
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordStrength {
    String message() default "Password must be at least 12 characters and include a letter and a digit";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
