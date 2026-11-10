package com.erp.erp_cloud.service.certificates;

import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateRequest;
import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.LegalDocumentTemplate;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.LegalDocumentTemplateRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers LegalDocumentTemplateService -- see
 * claude/propuesta-certificados-retencion-2026-11-07.md. The key
 * behavior under test is the default-fallback: a company with no
 * saved row for a key must still get a usable (non-blank) template
 * back, and saving must never touch another company's row.
 */
class LegalDocumentTemplateServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final String KEY = LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY;

    @Mock
    private LegalDocumentTemplateRepository repository;

    private LegalDocumentTemplateService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LegalDocumentTemplateService(repository);

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA S.A.S.");
        TenantContext.setContext(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getOrDefault() falls back to the built-in default when the company has no saved row")
    void getOrDefault_returnsBuiltInDefault_whenNoneSaved() {
        when(repository.findByCompanyIdAndTemplateKey(COMPANY_ID, KEY)).thenReturn(Optional.empty());

        LegalDocumentTemplateResponseDTO result = service.getOrDefault(KEY);

        assertThat(result.isDefault()).isTrue();
        assertThat(result.getBodyHtml()).isNotBlank();
        assertThat(result.getBodyHtml()).contains("{{company_name}}");
        assertThat(result.getFooterHtml()).isNotBlank();
    }

    @Test
    @DisplayName("getOrDefault() returns the saved row, not the default, once the company has one")
    void getOrDefault_returnsSavedRow_whenExists() {
        LegalDocumentTemplate saved = new LegalDocumentTemplate();
        saved.setTemplateKey(KEY);
        saved.setName("Mi plantilla");
        saved.setDocumentTitle("Mi titulo");
        saved.setBodyHtml("<p>cuerpo editado</p>");
        saved.setFooterHtml("<p>pie editado</p>");

        when(repository.findByCompanyIdAndTemplateKey(COMPANY_ID, KEY)).thenReturn(Optional.of(saved));

        LegalDocumentTemplateResponseDTO result = service.getOrDefault(KEY);

        assertThat(result.isDefault()).isFalse();
        assertThat(result.getBodyHtml()).isEqualTo("<p>cuerpo editado</p>");
        assertThat(result.getName()).isEqualTo("Mi plantilla");
    }

    @Test
    @DisplayName("builtInDefault() returns the hardcoded fallback even when the company already has a saved override")
    void builtInDefault_returnsHardcodedDefault_regardlessOfSavedRow() {
        // Deliberately never stubs the repository: builtInDefault() must
        // not consult it at all -- it is "Restaurar plantilla por
        // defecto", not getOrDefault(), so a saved override must never
        // leak into the result.
        LegalDocumentTemplateResponseDTO result = service.builtInDefault(KEY);

        assertThat(result.isDefault()).isTrue();
        assertThat(result.getBodyHtml()).contains("{{company_name}}");
        assertThat(result.getFooterHtml()).isNotBlank();
    }

    @Test
    @DisplayName("upsert() inserts a new row scoped to the current tenant when none exists yet")
    void upsert_createsNewRow_whenNoneExists() {
        when(repository.findByCompanyIdAndTemplateKey(COMPANY_ID, KEY)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(LegalDocumentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LegalDocumentTemplateRequest request = new LegalDocumentTemplateRequest();
        request.setName("Certificado de Retencion");
        request.setDocumentTitle("Certificado de Retencion en la Fuente");
        request.setBodyHtml("<p>{{third_party_name}}</p>");
        request.setFooterHtml("<p>pie</p>");

        LegalDocumentTemplateResponseDTO result = service.upsert(KEY, request);

        ArgumentCaptor<LegalDocumentTemplate> captor = ArgumentCaptor.forClass(LegalDocumentTemplate.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCompany().getId()).isEqualTo(COMPANY_ID);
        assertThat(captor.getValue().getTemplateKey()).isEqualTo(KEY);
        assertThat(result.isDefault()).isFalse();
        assertThat(result.getBodyHtml()).isEqualTo("<p>{{third_party_name}}</p>");
    }

    @Test
    @DisplayName("upsert() updates the existing row in place instead of inserting a duplicate")
    void upsert_updatesExistingRow() {
        LegalDocumentTemplate existing = new LegalDocumentTemplate();
        existing.setTemplateKey(KEY);
        existing.setName("Vieja");
        existing.setBodyHtml("<p>viejo</p>");

        when(repository.findByCompanyIdAndTemplateKey(COMPANY_ID, KEY)).thenReturn(Optional.of(existing));
        when(repository.save(org.mockito.ArgumentMatchers.any(LegalDocumentTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LegalDocumentTemplateRequest request = new LegalDocumentTemplateRequest();
        request.setName("Nueva");
        request.setBodyHtml("<p>nuevo</p>");

        service.upsert(KEY, request);

        assertThat(existing.getName()).isEqualTo("Nueva");
        assertThat(existing.getBodyHtml()).isEqualTo("<p>nuevo</p>");
    }

    @Test
    @DisplayName("upsert() rejects an unknown template key")
    void upsert_unknownKey_throws() {
        LegalDocumentTemplateRequest request = new LegalDocumentTemplateRequest();
        request.setName("x");
        request.setBodyHtml("x");

        assertThatThrownBy(() -> service.upsert("NOT_A_REAL_KEY", request))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNKNOWN_TEMPLATE_KEY");
    }
}
