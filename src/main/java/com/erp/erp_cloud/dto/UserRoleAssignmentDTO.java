package com.erp.erp_cloud.dto;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): one role a user holds IN A
 * SPECIFIC COMPANY -- never exposes roles the same user might hold in
 * some OTHER company, since the admin screen that consumes this only
 * ever operates within the current tenant.
 */
public record UserRoleAssignmentDTO(
        Long roleId,
        String roleName,
        String roleCode
) {}
