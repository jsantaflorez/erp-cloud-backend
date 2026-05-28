package com.erp.erp_cloud.security;

import java.util.List;

/**
 * Data transfer object to hold parsed JWT claims, avoiding multiple parsing execution steps per request.
 */
public record UserPrincipalClaims(
        String email,
        Long userId,
        Long companyId,
        List<String> authorities
) {}