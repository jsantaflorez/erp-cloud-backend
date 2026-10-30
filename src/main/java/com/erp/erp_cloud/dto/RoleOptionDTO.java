package com.erp.erp_cloud.dto;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): one entry in the role picker
 * for the current company (GET /api/v1/roles) -- company-specific
 * roles plus system-wide ones (Role.company == null).
 */
public record RoleOptionDTO(
        Long id,
        String name,
        String code
) {}
