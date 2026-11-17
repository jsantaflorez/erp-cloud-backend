package com.erp.erp_cloud.dto;

/**
 * Body of POST /api/v1/chart-of-accounts/reset. confirmation must be the
 * literal word "BORRAR": a server-side guard against an accidental call,
 * on top of the confirmation step in the UI.
 */
public record ChartOfAccountResetRequest(String confirmation) {
}
