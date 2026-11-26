package com.erp.erp_cloud.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Step 1 of the two-step login (B1, 2026-11-25): email + password only.
 * The company is chosen afterwards (POST /api/auth/select-company), so the
 * client no longer needs to know it before the password is verified.
 */
public record LoginRequest(
        @NotBlank(message = "Invalid credentials")
        @Email(message = "Invalid credentials")
        String email,

        @NotBlank(message = "Invalid credentials")
        String password
) {}
