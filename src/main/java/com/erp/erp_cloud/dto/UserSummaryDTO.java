package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): one row in the current
 * company's user-management screen. `active` is the user's GLOBAL
 * active flag (affects login everywhere, not just this company) --
 * the frontend is responsible for labeling that distinction clearly.
 * `roles` is always scoped to the CURRENT company only.
 */
public record UserSummaryDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        boolean active,
        List<UserRoleAssignmentDTO> roles
) {}
