package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): request body for
 * PATCH /api/v1/users/{id}/password -- an admin-triggered reset
 * (no old-password check, unlike a self-service change). The password
 * is global to the user, so this affects login in EVERY company they
 * have access to, not just the current one.
 */
public record ChangePasswordRequest(
        @NotBlank String newPassword
) {}
