package com.erp.erp_cloud.dto.certificates;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A company's resolved legal-document template (2026-11-07). {@code
 * isDefault} tells the admin screen whether this is the built-in
 * default (no row saved yet for this company) or an already-saved
 * override, so it can show "Restaurar plantilla por defecto" only
 * when there is something to restore FROM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalDocumentTemplateResponseDTO {
    private String templateKey;
    private String name;
    private String documentTitle;
    private String bodyHtml;
    private String footerHtml;
    private boolean isDefault;
}
