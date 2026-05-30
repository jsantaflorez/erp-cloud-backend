package com.erp.erp_cloud.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object representing the user credentials payload during the login execution phase.
 * It uses generic error messages to prevent credential enumeration attacks.
 *   multi-tenant company context during the login execution phase.
 */


public record LoginRequest(

        @NotBlank(message = "Invalid credentials format")
        String email,

        @NotBlank(message = "Invalid credentials format")
        String password,

        @NotNull(message = "Invalid credentials format")
                Long companyId // The target tenant context the user wishes to access
) {}