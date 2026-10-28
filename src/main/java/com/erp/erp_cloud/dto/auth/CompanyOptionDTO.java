package com.erp.erp_cloud.dto.auth;

/**
 * NEW (2026-10-26, Multiempresa Fase 1): one entry in the "companies I
 * can switch into" list (GET /api/v1/session/companies). Deliberately
 * flat and minimal -- just enough for the AppHeader dropdown to render
 * a label and send a companyId back to POST /switch-company.
 */
public record CompanyOptionDTO(
        Long id,
        String legalName,
        String tradeName,
        String taxId
) {}
