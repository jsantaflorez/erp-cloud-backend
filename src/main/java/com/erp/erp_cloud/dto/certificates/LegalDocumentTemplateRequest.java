package com.erp.erp_cloud.dto.certificates;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Upsert payload for a company's own copy of a legal-document template
 * (2026-11-07). {@code templateKey} travels in the URL path, not here --
 * the body only carries what the accountant actually edits.
 */
@Data
public class LegalDocumentTemplateRequest {

    @NotBlank
    private String name;

    private String documentTitle;

    @NotBlank
    private String bodyHtml;

    private String footerHtml;
}
