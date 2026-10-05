package com.erp.erp_cloud.dto;

import lombok.Data;

/**
 * NEW (2026-10-03): body for PATCH /v1/companies/me/closing-defaults.
 * Both nullable on purpose -- sending null for either clears that
 * suggestion (company goes back to "no default configured" for that
 * one), it does not mean "leave unchanged" (the endpoint always sets
 * both from this body, same as everywhere else in this codebase that
 * takes a full replacement request rather than a partial patch).
 */
@Data
public class CompanyClosingDefaultsRequest {
    private Long defaultGainAccountId;
    private Long defaultLossAccountId;
}
