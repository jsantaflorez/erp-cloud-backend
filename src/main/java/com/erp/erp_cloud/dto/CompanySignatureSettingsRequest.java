package com.erp.erp_cloud.dto;

import lombok.Data;

/**
 * NEW (2026-11-09): body for PATCH /v1/companies/me/signature-settings --
 * see claude/progreso-2026-11-09.md. Same "always sets everything, null
 * clears" convention as CompanyClosingDefaultsRequest: sending null for
 * logoDataUrl or any signer field removes it, it does not mean "leave
 * unchanged".
 *
 * logoDataUrl is validated (format + size) in CompanyService, not here
 * with bean-validation annotations -- a bad logo needs a specific,
 * stable error code (LOGO_TOO_LARGE / INVALID_LOGO_FORMAT) the frontend
 * can map to a clear message, same pattern already used for every other
 * business-rule check in this codebase (InvalidOperationException),
 * rather than a generic MethodArgumentNotValidException message.
 */
@Data
public class CompanySignatureSettingsRequest {
    private String logoDataUrl;
    private String signer1Name;
    private String signer1Role;
    private String signer1IdNumber;
    private String signer1ProfessionalLicense;
    private String signer2Name;
    private String signer2Role;
    private String signer2IdNumber;
    private String signer2ProfessionalLicense;
}
