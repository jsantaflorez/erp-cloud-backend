package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): request body for
 * POST /api/v1/users.
 *
 * `password`, `firstName` and `lastName` are validated CONDITIONALLY in
 * UserManagementService, not here: they're only required when `email`
 * doesn't already belong to a registered user (a brand-new person). For
 * an existing person just being granted access to a new company, all
 * three are ignored entirely -- their existing name/password keep
 * working everywhere, this call only adds UserRole rows.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        String firstName,
        String lastName,
        String password,
        @NotEmpty List<Long> roleIds
) {}
