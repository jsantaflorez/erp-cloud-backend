package com.erp.erp_cloud.dto.auth;

import java.util.Set;

public record AuthResponse(
        String token,
        String type,
        String email,
        String fullName,
        Long companyId,
        Set<String> roles,
        long expiresIn
) {}