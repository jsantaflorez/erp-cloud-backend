package com.erp.erp_cloud.dto;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): answer to "is this email
 * already a registered user anywhere in the system?" -- lets the
 * frontend's "add user" form decide whether to ask for a new password
 * (brand-new person) or skip straight to picking roles (existing
 * person, just being granted access to this company too).
 */
public record UserLookupResultDTO(
        boolean exists,
        String fullName
) {}
