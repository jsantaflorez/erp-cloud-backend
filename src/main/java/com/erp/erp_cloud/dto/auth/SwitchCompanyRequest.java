package com.erp.erp_cloud.dto.auth;

import jakarta.validation.constraints.NotNull;

/**
 * NEW (2026-10-26, Multiempresa Fase 1): request body for
 * POST /api/v1/session/switch-company.
 */
public record SwitchCompanyRequest(
        @NotNull Long companyId
) {}
