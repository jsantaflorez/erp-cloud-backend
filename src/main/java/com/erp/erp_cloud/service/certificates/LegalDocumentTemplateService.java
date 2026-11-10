package com.erp.erp_cloud.service.certificates;

import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateRequest;
import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateResponseDTO;
import com.erp.erp_cloud.entity.LegalDocumentTemplate;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.LegalDocumentTemplateRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD + default-fallback for a company's editable legal-document
 * templates (2026-11-07) -- see the propuesta doc
 * (claude/propuesta-certificados-retencion-2026-11-07.md) for the full
 * design. A company that never saved a row for a given key simply
 * gets the built-in default back from getOrDefault() -- nothing is
 * inserted until the admin screen actually saves an edit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalDocumentTemplateService extends TenantAwareService {

    public static final String WITHHOLDING_CERTIFICATE_KEY = "WITHHOLDING_CERTIFICATE";

    private static final String DEFAULT_BODY_RESOURCE =
            "templates/certificates/withholding-certificate-default-body.html";
    private static final String DEFAULT_FOOTER_RESOURCE =
            "templates/certificates/withholding-certificate-default-footer.html";

    private final LegalDocumentTemplateRepository repository;

    public LegalDocumentTemplateResponseDTO getOrDefault(String templateKey) {
        Long companyId = currentTenantId();
        return repository.findByCompanyIdAndTemplateKey(companyId, templateKey)
                .map(this::toResponse)
                .orElseGet(() -> builtInDefault(templateKey));
    }

    public List<LegalDocumentTemplateResponseDTO> list() {
        Long companyId = currentTenantId();
        return repository.findByCompanyIdOrderByTemplateKeyAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LegalDocumentTemplateResponseDTO upsert(String templateKey, LegalDocumentTemplateRequest request) {
        requireKnownKey(templateKey);
        Long companyId = currentTenantId();
        LegalDocumentTemplate entity = repository.findByCompanyIdAndTemplateKey(companyId, templateKey)
                .orElseGet(() -> {
                    LegalDocumentTemplate created = new LegalDocumentTemplate();
                    created.setCompany(currentCompany());
                    created.setTemplateKey(templateKey);
                    return created;
                });
        entity.setName(request.getName());
        entity.setDocumentTitle(request.getDocumentTitle());
        entity.setBodyHtml(request.getBodyHtml());
        entity.setFooterHtml(request.getFooterHtml());
        entity.setActive(true);
        return toResponse(repository.save(entity));
    }

    private void requireKnownKey(String templateKey) {
        if (!WITHHOLDING_CERTIFICATE_KEY.equals(templateKey)) {
            throw new InvalidOperationException(
                    "Unknown legal document template key: " + templateKey, "UNKNOWN_TEMPLATE_KEY");
        }
    }

    // PUBLIC (not private) on purpose: the admin screen's "Restaurar
    // plantilla por defecto" button needs the hardcoded fallback text
    // even after the company already has a saved override on file --
    // getOrDefault() above can't serve that because it prefers the
    // saved row whenever one exists. Still reuses requireKnownKey() and
    // the same classpath resources, so there is exactly one place that
    // knows what the built-in default text is.
    public LegalDocumentTemplateResponseDTO builtInDefault(String templateKey) {
        requireKnownKey(templateKey);
        return LegalDocumentTemplateResponseDTO.builder()
                .templateKey(templateKey)
                .name("Certificado de Retención")
                // CHANGED (2026-11-09): now actually resolved through
                // Mustache (see WithholdingCertificateService.renderHtml()'s
                // Stage-1 fix), so this doubles as the shipped example of
                // a dynamic title -- "AÑO GRAVABLE {{tax_year}}" is NOT
                // repeated here on purpose, the skeleton already prints
                // that line unconditionally right under whatever title
                // resolves, so it can never go missing even if an admin
                // overwrites this field without including the year.
                .documentTitle("CERTIFICADO DE RETENCIÓN DE {{tax_type_label}}")
                .bodyHtml(ClasspathTemplateLoader.readUtf8(DEFAULT_BODY_RESOURCE))
                .footerHtml(ClasspathTemplateLoader.readUtf8(DEFAULT_FOOTER_RESOURCE))
                .isDefault(true)
                .build();
    }

    private LegalDocumentTemplateResponseDTO toResponse(LegalDocumentTemplate entity) {
        return LegalDocumentTemplateResponseDTO.builder()
                .templateKey(entity.getTemplateKey())
                .name(entity.getName())
                .documentTitle(entity.getDocumentTitle())
                .bodyHtml(entity.getBodyHtml())
                .footerHtml(entity.getFooterHtml())
                .isDefault(false)
                .build();
    }
}
