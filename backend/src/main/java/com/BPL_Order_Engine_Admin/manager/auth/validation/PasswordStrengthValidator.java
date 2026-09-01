package com.BPL_Order_Engine_Admin.manager.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordStrengthValidator implements ConstraintValidator<PasswordStrength, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return false;
        }
        int len = value.length();
        if (len < 12 || len > 128) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                return false;
            }
            if (!hasLetter && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                hasLetter = true;
            }
            if (!hasDigit && (c >= '0' && c <= '9')) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return true;
            }
        }
        return hasLetter && hasDigit;
    }
}
