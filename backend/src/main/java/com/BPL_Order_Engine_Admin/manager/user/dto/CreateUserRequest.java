package com.BPL_Order_Engine_Admin.manager.user.dto;

import com.BPL_Order_Engine_Admin.manager.auth.validation.PasswordStrength;
import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
    @NotBlank @Size(max = 64) String username,
    @PasswordStrength String password,
    @NotNull RoleType role,
    @NotNull List<String> assignedEngineCodes
) {}
