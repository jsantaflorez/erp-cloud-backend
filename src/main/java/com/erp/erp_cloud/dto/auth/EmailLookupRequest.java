package com.erp.erp_cloud.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * NEW (2026-10-28, Multiempresa Fase 2): request body for
 * POST /api/auth/companies -- the Login screen's "which companies can
 * this email log into" lookup, used to populate its company dropdown
 * BEFORE the password is verified (no JWT exists yet at this point).
 */
public record EmailLookupRequest(
        @NotBlank @Email String email
) {}
