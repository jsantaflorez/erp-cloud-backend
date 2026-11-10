package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal read-only view of the current tenant's Company, for frontend
 * needs that don't warrant a full Company CRUD (there is none yet --
 * companies are provisioned directly in the database today). chartTemplate
 * is null when the company has no chart-of-accounts template configured
 * (the correct default; see ChartTemplateType).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponseDTO {
    private Long id;
    private String legalName;
    private String tradeName;
    private String chartTemplate; // null, "COMERCIAL" or "SOLIDARIO"

    // NEW (2026-10-03): suggested Ganancia/Pérdida accounts for the
    // year-end closing screen. Null means not configured yet -- see
    // Company.defaultGainAccount/defaultLossAccount.
    private Long defaultGainAccountId;
    private String defaultGainAccountCode;
    private String defaultGainAccountName;
    private Long defaultLossAccountId;
    private String defaultLossAccountCode;
    private String defaultLossAccountName;

    // NEW (2026-11-09): display-only context for the new "Datos de la
    // Empresa" admin screen -- this DTO never had anywhere to show
    // which company you're editing beyond its name. Not editable from
    // that screen (or any screen yet); still provisioned directly in
    // the database, same as CompanyService's own class javadoc says.
    private String taxId;
    private String verificationDigit;
    private String address;
    private String cityName;

    // NEW (2026-11-09): logo + signature-block settings, see
    // claude/progreso-2026-11-09.md and Company.java. logoDataUrl is a
    // self-contained "data:image/...;base64,..." string, never a
    // hosted URL -- the frontend can drop it straight into an <img src>
    // with no separate fetch. Null in any signer field just means that
    // slot (or sub-field) was never configured.
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
