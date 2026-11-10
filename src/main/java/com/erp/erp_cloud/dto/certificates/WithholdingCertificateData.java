package com.erp.erp_cloud.dto.certificates;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything a Withholding Certificate needs to render (2026-11-07):
 * the scope-level variables listed in the propuesta doc ({{company_name}},
 * {{third_party_name}}, {{nit}}, {{tax_year}}, {{total_base}},
 * {{total_withheld}}, ...) plus the ones needed to issue a complete
 * document (emisor data, not just the receptor). Built once by
 * WithholdingCertificateService.getCertificateData() and handed to
 * BOTH the preview endpoint and the PDF endpoint via the same
 * renderHtml() call, so they can never drift apart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithholdingCertificateData {
    private String companyName;
    private String companyNit;
    private String companyAddress;
    private String companyCity;

    private String thirdPartyName;
    private String thirdPartyNit;

    private int taxYear;
    private String taxType;
    private String taxTypeLabel;

    private BigDecimal totalBase;
    private BigDecimal totalWithheld;

    private List<WithholdingCertificateLine> lines;

    // 2026-11-10: una fila por concepto (impuesto + tarifa) con base y valor
    // sumados -- es lo que muestra la tabla del certificado. "lines" sigue
    // existiendo, un renglón por movimiento, para plantillas que quieran el
    // desglose detallado con {{#lines}}.
    private List<WithholdingCertificateLine> conceptLines;

    private String issueDate;
    private String issueCity;

    // NEW (2026-11-09): logo + up to two named signers, both pulled
    // straight from Company -- see claude/progreso-2026-11-09.md.
    // logoDataUrl is a self-contained "data:image/...;base64,..." string
    // (never a hosted URL). Each signerN* group is independently
    // null-or-complete-enough; WithholdingCertificateService decides
    // has_signer1/has_signer2 from these when building the Mustache
    // scope, the skeleton never re-derives that itself.
    private String companyLogoDataUrl;

    private String signer1Name;
    private String signer1Role;
    private String signer1IdNumber;
    private String signer1ProfessionalLicense;

    private String signer2Name;
    private String signer2Role;
    private String signer2IdNumber;
    private String signer2ProfessionalLicense;
}
