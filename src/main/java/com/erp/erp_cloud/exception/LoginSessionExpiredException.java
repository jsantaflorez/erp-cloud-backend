package com.erp.erp_cloud.exception;

/**
 * B1 (2026-11-25): the pre-session token of the two-step login is missing,
 * invalid or expired (paso 2 de /api/auth/select-company). Mapped to 401
 * LOGIN_SESSION_EXPIRED: the Login screen goes back to the first step.
 */
public class LoginSessionExpiredException extends RuntimeException {

    public LoginSessionExpiredException() {
        super("Login session missing, invalid or expired.");
    }
}
