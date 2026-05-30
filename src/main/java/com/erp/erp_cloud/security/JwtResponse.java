package com.erp.erp_cloud.security;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Data Transfer Object containing the authentication token, its expiration lifespan,
 * and essential non-sensitive user identity metrics.
 */
public record JwtResponse(
        String token,
        String tokenType,
        long expiresIn, // Lifespan of the token in seconds for client-side refresh scheduling
        UserSummary user
) {
    /**
     * Compact primary constructor surrogate to enforce standard token defaults.
     */
    public JwtResponse(String token, long expiresIn, UserSummary user) {
        this(token, "Bearer", expiresIn, user);
    }

    /**
     * Inner record to wrap essential non-sensitive user identity context variables.
     * High-level roles are included for UI behavior, while user profile data handles potential nulls safely.
     */
    public record UserSummary(
            Long id,
            String email,
            @Nullable String fullName, // Explicitly handles incomplete or lazy profile states defensively
            Long companyId,
            List<String> roles // Safe high-level groupings for UI rendering logic
    ) {}
}