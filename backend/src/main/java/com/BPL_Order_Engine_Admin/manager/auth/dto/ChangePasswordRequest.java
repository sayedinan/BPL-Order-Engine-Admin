package com.BPL_Order_Engine_Admin.manager.auth.dto;

import com.BPL_Order_Engine_Admin.manager.auth.validation.PasswordStrength;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @PasswordStrength String newPassword
) {}
