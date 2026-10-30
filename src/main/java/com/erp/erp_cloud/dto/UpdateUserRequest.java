package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): request body for
 * PATCH /api/v1/users/{id}. Email is deliberately NOT editable here --
 * it's the login identity, changing it has implications beyond this
 * screen's scope.
 */
public record UpdateUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName
) {}
