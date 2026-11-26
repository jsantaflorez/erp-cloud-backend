package com.erp.erp_cloud.security;

/** B1 (2026-11-25): claims of a validated pre-session token (step 1 of the login). */
public record PreAuthClaims(String email, Long userId) {}
