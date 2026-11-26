package com.erp.erp_cloud.dto.auth;

import java.util.List;

/**
 * B1 (2026-11-25): answer of step 1 of the two-step login
 * (POST /api/auth/login, email + password). Carries a short-lived
 * pre-session token whose ONLY use is step 2 (POST /api/auth/select-company)
 * -- the JWT filter rejects it everywhere else -- and the companies this
 * user can enter, with name and NIT (shown only after the password was
 * verified, so it is no longer a public directory).
 */
public record PreAuthResponse(
        String preAuthToken,
        long expiresIn,
        String email,
        String fullName,
        List<CompanyOptionDTO> companies
) {}
