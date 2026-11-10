package com.erp.erp_cloud.service.certificates;

import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateResponseDTO;
import com.erp.erp_cloud.dto.certificates.ThirdPartyOptionDTO;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateData;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateLine;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.TaxCategory;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Covers WithholdingCertificateService -- see
 * claude/propuesta-certificados-retencion-2026-11-07.md for the full
 * design. Deliberately reuses the exact JournalEntryRepository.findTaxItems()
 * fixtures style already proven in TaxDetailReportServiceTest, since
 * the service reuses that same query.
 */
class WithholdingCertificateServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private ThirdPartyRepository thirdPartyRepository;

    @Mock
    private TaxRepository taxRepository;

    @Mock
    private LegalDocumentTemplateService templateService;

    private WithholdingCertificateService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new WithholdingCertificateService(
                journalEntryRepository, thirdPartyRepository, taxRepository, templateService);

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setLegalName("EMPRESA DE PRUEBA S.A.S.");
        company.setTaxId("900111222");
        company.setVerificationDigit("3");
        company.setAddress("Calle 1 # 2-3");
        City city = new City();
        city.setName("BOGOTA");
        company.setCity(city);
        TenantContext.setContext(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ChartOfAccounts account(String code, String name) {
        ChartOfAccounts acc = new ChartOfAccounts();
        acc.setCode(code);
        acc.setName(name);
        return acc;
    }

    private Tax tax(String type, TaxCategory category, String rate) {
        Tax tax = new Tax();
        tax.setCode(type);
        tax.setName(type);
        tax.setType(type);
        tax.setCategory(category);
        tax.setRate(new BigDecimal(rate));
        tax.setSign("C");
        tax.setActive(true);
        return tax;
    }

    private ThirdParty thirdParty(String document, String businessName) {
        ThirdParty tp = new ThirdParty();
        tp.setDocumentNumber(document);
        tp.setBusinessName(businessName);
        tp.setVerificationDigit(7);
        return tp;
    }

    private JournalEntry entry(LocalDate date, String docNumber) {
        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(date);
        entry.setDocumentNumber(docNumber);
        entry.setDescription("Comprobante de prueba");
        return entry;
    }

    private JournalEntryItem item(JournalEntry je, ChartOfAccounts acc, Tax tax, ThirdParty tp,
                                   BigDecimal base, BigDecimal debit, BigDecimal credit) {
        JournalEntryItem item = new JournalEntryItem();
        item.setJournalEntry(je);
        item.setAccount(acc);
        item.setTax(tax);
        item.setTaxBase(base);
        item.setThirdParty(tp);
        item.setDebit(debit);
        item.setCredit(credit);
        return item;
    }

    @Test
    @DisplayName("getCertificateData() sums base/withheld across every line, reading the value from whichever side is non-zero")
    void getCertificateData_computesTotals() {
        ChartOfAccounts acc = account("236540", "Retefuente por Pagar");
        Tax reteTax = tax("RETEFUENTE", TaxCategory.RETENCION, "3.5");
        ThirdParty tp = thirdParty("900123456", "PROVEEDOR SAS");

        JournalEntryItem i1 = item(entry(LocalDate.of(2026, 3, 5), "FE-1"), acc, reteTax, tp,
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("35000"));
        JournalEntryItem i2 = item(entry(LocalDate.of(2026, 7, 10), "FE-2"), acc, reteTax, tp,
                new BigDecimal("2000000"), BigDecimal.ZERO, new BigDecimal("70000"));

        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "900123456"))
                .thenReturn(Optional.of(tp));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("900123456")))
                .thenReturn(List.of(i1, i2));

        WithholdingCertificateData data = service.getCertificateData(2026, "retefuente", "900123456");

        assertThat(data.getTotalBase()).isEqualByComparingTo("3000000");
        assertThat(data.getTotalWithheld()).isEqualByComparingTo("105000");
        assertThat(data.getThirdPartyName()).isEqualTo("PROVEEDOR SAS");
        assertThat(data.getTaxType()).isEqualTo("RETEFUENTE");
        assertThat(data.getLines()).hasSize(2);
        assertThat(data.getCompanyName()).isEqualTo("EMPRESA DE PRUEBA S.A.S.");
        assertThat(data.getCompanyNit()).isEqualTo("900111222-3");
    }

    @Test
    @DisplayName("getCertificateData() rejects a third party/year/type with no retention movements")
    void getCertificateData_noMovements_throws() {
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "900123456"))
                .thenReturn(Optional.of(thirdParty("900123456", "PROVEEDOR SAS")));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("900123456")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getCertificateData(2026, "RETEFUENTE", "900123456"))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "NO_RETENTION_MOVEMENTS");
    }

    @Test
    @DisplayName("getCertificateData() rejects a tax year in the future")
    void getCertificateData_futureYear_throws() {
        int nextYear = LocalDate.now().getYear() + 1;
        assertThatThrownBy(() -> service.getCertificateData(nextYear, "RETEFUENTE", "900123456"))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TAX_YEAR");
    }

    @Test
    @DisplayName("getCertificateData() rejects a third party not found for this (tenant-scoped) company")
    void getCertificateData_thirdPartyNotFound_throws() {
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "999999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCertificateData(2026, "RETEFUENTE", "999999999"))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "THIRD_PARTY_NOT_FOUND");

        // The lookup is always scoped to the tenant bound by TenantContext --
        // never a bare findById -- which is what keeps this isolated per company.
        org.mockito.Mockito.verify(thirdPartyRepository)
                .findByCompanyIdAndDocumentNumber(COMPANY_ID, "999999999");
    }

    @Test
    @DisplayName("getAvailableRetentionTaxTypes() keeps only RETENCION-category types, deduplicated and sorted")
    void getParameters_filtersTaxTypesByRetentionCategory() {
        ChartOfAccounts acc = account("236540", "Retefuente por Pagar");
        when(taxRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(List.of(
                tax("RETEICA", TaxCategory.RETENCION, "1.0"),
                tax("RETEFUENTE", TaxCategory.RETENCION, "3.5"),
                tax("RETEFUENTE", TaxCategory.RETENCION, "2.5"),
                tax("IVA_GENERADO", TaxCategory.IVA, "19")
        ));

        var parameters = service.getParameters(2026, null);

        assertThat(parameters.getTaxTypes()).containsExactly("RETEFUENTE", "RETEICA");
        assertThat(parameters.getThirdParties()).isEmpty();
    }

    @Test
    @DisplayName("getParameters() with a taxType also returns the distinct third parties with movements that year")
    void getParameters_withTaxType_returnsDistinctThirdParties() {
        ChartOfAccounts acc = account("236540", "Retefuente por Pagar");
        Tax reteTax = tax("RETEFUENTE", TaxCategory.RETENCION, "3.5");
        ThirdParty tpA = thirdParty("900111111", "PROVEEDOR A");
        ThirdParty tpB = thirdParty("900222222", "PROVEEDOR B");

        when(taxRepository.findByCompanyIdOrderByCodeAsc(COMPANY_ID)).thenReturn(List.of(reteTax));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), isNull()))
                .thenReturn(List.of(
                        item(entry(LocalDate.of(2026, 1, 10), "FE-1"), acc, reteTax, tpA,
                                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("3500")),
                        item(entry(LocalDate.of(2026, 2, 10), "FE-2"), acc, reteTax, tpA,
                                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("3500")),
                        item(entry(LocalDate.of(2026, 3, 10), "FE-3"), acc, reteTax, tpB,
                                new BigDecimal("200000"), BigDecimal.ZERO, new BigDecimal("7000"))
                ));

        var parameters = service.getParameters(2026, "RETEFUENTE");

        assertThat(parameters.getThirdParties())
                .extracting(ThirdPartyOptionDTO::getDocumentNumber)
                .containsExactlyInAnyOrder("900111111", "900222222");
    }

    @Test
    @DisplayName("renderHtml() resolves {{variable}} placeholders and the {{#lines}} block against the company's saved template")
    void renderHtml_substitutesVariablesAndLinesBlock() {
        LegalDocumentTemplateResponseDTO template = LegalDocumentTemplateResponseDTO.builder()
                .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                .name("Certificado de Retencion")
                .documentTitle("Certificado de Retencion en la Fuente")
                .bodyHtml("<p>Retuvimos a {{third_party_name}} (NIT {{nit}}) la suma de {{total_withheld}}.</p>"
                        + "{{#has_lines}}<ul>{{#lines}}<li>{{detail}}: {{value}}</li>{{/lines}}</ul>{{/has_lines}}")
                .footerHtml("<small>Expedido por {{company_name}}.</small>")
                .isDefault(false)
                .build();
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(template);

        WithholdingCertificateData data = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .companyNit("900111222-3")
                .thirdPartyName("PROVEEDOR SAS")
                .thirdPartyNit("900123456-7")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(new BigDecimal("1000000"))
                .totalWithheld(new BigDecimal("35000"))
                .lines(List.of(com.erp.erp_cloud.dto.certificates.WithholdingCertificateLine.builder()
                        .entryDate("2026-03-05")
                        .documentNumber("FE-1")
                        .detail("Honorarios")
                        .base(new BigDecimal("1000000"))
                        .rate(new BigDecimal("3.5"))
                        .value(new BigDecimal("35000"))
                        .build()))
                .issueDate("7 de noviembre de 2026")
                .build();

        String html = service.renderHtml(data);

        String formattedWithheld = NumberFormat.getIntegerInstance(new Locale("es", "CO"))
                .format(new BigDecimal("35000"));

        assertThat(html).contains("PROVEEDOR SAS");
        assertThat(html).contains("900123456-7");
        assertThat(html).contains(formattedWithheld);
        assertThat(html).contains("Honorarios");
        assertThat(html).contains("EMPRESA DE PRUEBA S.A.S.");
        assertThat(html).contains("Certificado de Retencion en la Fuente");
        // The raw {{tags}} must never survive into the final HTML.
        assertThat(html).doesNotContain("{{");
    }

    @Test
    @DisplayName("resolveZipDocuments() rejects a blank tax type before touching the document list")
    void resolveZipDocuments_blankTaxType_throws() {
        assertThatThrownBy(() -> service.resolveZipDocuments(2026, "  ", List.of("900123456")))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TAX_TYPE_REQUIRED");
    }

    @Test
    @DisplayName("resolveZipDocuments() rejects an empty selection, including one made entirely of blanks/duplicates")
    void resolveZipDocuments_emptySelection_throws() {
        assertThatThrownBy(() -> service.resolveZipDocuments(2026, "RETEFUENTE", List.of()))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "THIRD_PARTY_REQUIRED");

        assertThatThrownBy(() -> service.resolveZipDocuments(2026, "RETEFUENTE", List.of("  ", "")))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "THIRD_PARTY_REQUIRED");
    }

    @Test
    @DisplayName("resolveZipDocuments() trims and de-duplicates the requested document numbers")
    void resolveZipDocuments_trimsAndDeduplicates() {
        List<String> resolved = service.resolveZipDocuments(
                2026, "RETEFUENTE", List.of(" 900123456 ", "900123456", "900222222"));

        assertThat(resolved).containsExactly("900123456", "900222222");
    }

    @Test
    @DisplayName("writeZip() streams one PDF entry per resolvable third party and skips one that fails, without aborting the batch")
    void writeZip_streamsEntriesAndSkipsFailures() throws Exception {
        ChartOfAccounts acc = account("236540", "Retefuente por Pagar");
        Tax reteTax = tax("RETEFUENTE", TaxCategory.RETENCION, "3.5");
        ThirdParty ok = thirdParty("900123456", "PROVEEDOR OK SAS");

        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "900123456"))
                .thenReturn(Optional.of(ok));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("900123456")))
                .thenReturn(List.of(item(entry(LocalDate.of(2026, 3, 5), "FE-1"), acc, reteTax, ok,
                        new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("35000"))));

        // This one resolves to a third party that exists but has no
        // movements for this year/type -- getCertificateData() throws
        // NO_RETENTION_MOVEMENTS, and writeZip() must skip it rather
        // than losing the entry already written above.
        ThirdParty failing = thirdParty("900999999", "PROVEEDOR SIN MOVIMIENTOS SAS");
        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "900999999"))
                .thenReturn(Optional.of(failing));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("900999999")))
                .thenReturn(List.of());

        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("Certificado de Retencion en la Fuente")
                        .bodyHtml("<p>{{third_party_name}} - {{total_withheld}}</p>")
                        .footerHtml("")
                        .isDefault(true)
                        .build());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeZip(2026, "RETEFUENTE", List.of("900123456", "900999999"), out);

        List<String> entryNames = new java.util.ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                assertThat(zis.readAllBytes()).isNotEmpty();
            }
        }

        assertThat(entryNames).containsExactly("certificado-retencion-900123456-RETEFUENTE-2026.pdf");
    }

    @Test
    @DisplayName("renderHtml() resolves {{variables}} inside the document title too, not just body/footer")
    void renderHtml_resolvesVariablesInDocumentTitle() {
        // FIX (2026-11-09): documentTitle used to be inserted raw into
        // the skeleton's scope, so a title like this would have printed
        // the literal "{{tax_type_label}}" braces instead of RETEFUENTE.
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("CERTIFICADO DE RETENCION DE {{tax_type_label}}")
                        .bodyHtml("<p>Cuerpo.</p>")
                        .footerHtml("")
                        .isDefault(true)
                        .build());

        WithholdingCertificateData data = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("PROVEEDOR SAS")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(BigDecimal.ZERO)
                .totalWithheld(BigDecimal.ZERO)
                .lines(List.of())
                .build();

        String html = service.renderHtml(data);

        assertThat(html).contains("CERTIFICADO DE RETENCION DE RETEFUENTE");
        assertThat(html).doesNotContain("{{tax_type_label}}");
    }

    @Test
    @DisplayName("renderHtml() embeds the company logo when configured and omits the <img> tag when it's not")
    void renderHtml_logoShownOnlyWhenConfigured() {
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("Certificado")
                        .bodyHtml("<p>Cuerpo.</p>")
                        .footerHtml("")
                        .isDefault(true)
                        .build());

        WithholdingCertificateData withLogo = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("PROVEEDOR SAS")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(BigDecimal.ZERO)
                .totalWithheld(BigDecimal.ZERO)
                .lines(List.of())
                .companyLogoDataUrl("data:image/png;base64,AAAA")
                .build();

        String htmlWithLogo = service.renderHtml(withLogo);
        assertThat(htmlWithLogo).contains("<img src=\"data:image/png;base64,AAAA\"");

        // No .companyLogoDataUrl(...) call at all here -- Lombok's plain
        // @Builder (no toBuilder = true on this DTO) leaves it null by
        // default, same as a company that never configured a logo.
        WithholdingCertificateData withoutLogo = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("PROVEEDOR SAS")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(BigDecimal.ZERO)
                .totalWithheld(BigDecimal.ZERO)
                .lines(List.of())
                .build();
        String htmlWithoutLogo = service.renderHtml(withoutLogo);
        assertThat(htmlWithoutLogo).doesNotContain("<img");
    }

    @Test
    @DisplayName("renderHtml() renders both named signers when configured, with C.C./T.P. only when those fields are set")
    void renderHtml_namedSignersRenderConditionally() {
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("Certificado")
                        .bodyHtml("<p>Cuerpo.</p>")
                        .footerHtml("")
                        .isDefault(true)
                        .build());

        WithholdingCertificateData data = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("PROVEEDOR SAS")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(BigDecimal.ZERO)
                .totalWithheld(BigDecimal.ZERO)
                .lines(List.of())
                .signer1Name("JUAN PEREZ")
                .signer1Role("Representante Legal")
                .signer1IdNumber("12345678")
                .signer2Name("MARIA GOMEZ")
                .signer2Role("Contadora")
                .signer2ProfessionalLicense("98765-T")
                .build();

        String html = service.renderHtml(data);

        assertThat(html).contains("JUAN PEREZ");
        assertThat(html).contains("C.C. 12345678");
        assertThat(html).contains("MARIA GOMEZ");
        assertThat(html).contains("T.P. 98765-T");
        // No generic fallback lines once real signers are configured --
        // {{^has_any_signer}} is false, so that whole block is skipped.
        assertThat(html).doesNotContain("REPRESENTANTE LEGAL");
        assertThat(html).doesNotContain(">CONTADOR<");
    }

    @Test
    @DisplayName("renderHtml() falls back to the generic unlabeled signature lines when no signer is configured")
    void renderHtml_fallsBackToGenericSignatureLines_whenNoSignerConfigured() {
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("Certificado")
                        .bodyHtml("<p>Cuerpo.</p>")
                        .footerHtml("")
                        .isDefault(true)
                        .build());

        WithholdingCertificateData data = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("PROVEEDOR SAS")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(BigDecimal.ZERO)
                .totalWithheld(BigDecimal.ZERO)
                .lines(List.of())
                .build();

        String html = service.renderHtml(data);

        assertThat(html).contains("REPRESENTANTE LEGAL");
        assertThat(html).contains("CONTADOR");
    }

    @Test
    @DisplayName("renderHtml() converts a stray named HTML entity from a company's own saved template "
            + "into its literal character, instead of letting it reach openhtmltopdf's strict XML parser")
    void renderHtml_sanitizesStrayNamedHtmlEntityFromCustomTemplate() {
        // Reproduces a real bug (2026-11-09): a company's OWN row in
        // legal_document_templates, saved before the built-in default
        // files were corrected, still had "&ntilde;" baked into its text
        // -- unrelated to this test's mocked template, but renderHtml()
        // can't tell the difference between that and the classpath
        // default, so it must sanitize whatever it's handed either way.
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("Certificado")
                        .bodyHtml("<p>Durante el a&ntilde;o gravable, &amp; dem&aacute;s.</p>")
                        .footerHtml("")
                        .isDefault(false)
                        .build());

        WithholdingCertificateData data = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("PROVEEDOR SAS")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(BigDecimal.ZERO)
                .totalWithheld(BigDecimal.ZERO)
                .lines(List.of())
                .build();

        String html = service.renderHtml(data);

        assertThat(html).contains("Durante el año gravable");
        assertThat(html).contains("demás");
        assertThat(html).doesNotContain("&ntilde;");
        assertThat(html).doesNotContain("&aacute;");
        // The 5 entities XML already understands are left exactly as-is,
        // never decoded back into a literal character that would need
        // escaping again.
        assertThat(html).contains("&amp;");
    }

    @Test
    @DisplayName("getCertificateData() groups movements by concept (tax name + rate) and uses the tax name, not the line description, as concept")
    void getCertificateData_groupsByConcept() {
        ChartOfAccounts acc = account("236515", "Retención honorarios");
        Tax honorarios = tax("RETEFUENTE", TaxCategory.RETENCION, "10");
        honorarios.setName("Retención por honorarios 10%");
        ThirdParty tp = thirdParty("110589187", "JOSE LUIS RUIZ AVILA");

        JournalEntryItem i1 = item(entry(LocalDate.of(2026, 3, 5), "CE-1"), acc, honorarios, tp,
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("100000"));
        i1.setDescription("JOSE LUIS RUIZ AVILA ASESORIA");
        JournalEntryItem i2 = item(entry(LocalDate.of(2026, 7, 10), "CE-2"), acc, honorarios, tp,
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("100000"));
        i2.setDescription("JOSE LUIS RUIZ AVILA ASESORIA");

        when(thirdPartyRepository.findByCompanyIdAndDocumentNumber(COMPANY_ID, "110589187"))
                .thenReturn(Optional.of(tp));
        when(journalEntryRepository.findTaxItems(
                eq(COMPANY_ID), eq(TaxCategory.RETENCION), any(), any(), eq("RETEFUENTE"), eq("110589187")))
                .thenReturn(List.of(i1, i2));

        WithholdingCertificateData data = service.getCertificateData(2026, "RETEFUENTE", "110589187");

        assertThat(data.getLines()).hasSize(2);
        assertThat(data.getConceptLines()).hasSize(1);
        assertThat(data.getConceptLines().get(0).getConcept()).isEqualTo("Retención por honorarios 10%");
        assertThat(data.getConceptLines().get(0).getBase()).isEqualByComparingTo("2000000");
        assertThat(data.getConceptLines().get(0).getValue()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("renderHtml() shows the concept in the table and lays signatures out side by side without flexbox (unsupported by openhtmltopdf)")
    void renderHtml_conceptColumnAndSideBySideSignatures() {
        when(templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY))
                .thenReturn(LegalDocumentTemplateResponseDTO.builder()
                        .templateKey(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY)
                        .documentTitle("Certificado")
                        .bodyHtml("<p>Cuerpo.</p>")
                        .footerHtml("")
                        .isDefault(true)
                        .build());

        WithholdingCertificateData data = WithholdingCertificateData.builder()
                .companyName("EMPRESA DE PRUEBA S.A.S.")
                .thirdPartyName("JOSE LUIS RUIZ AVILA")
                .taxYear(2026)
                .taxType("RETEFUENTE")
                .taxTypeLabel("RETEFUENTE")
                .totalBase(new BigDecimal("2000000"))
                .totalWithheld(new BigDecimal("200000"))
                .lines(List.of(
                        com.erp.erp_cloud.dto.certificates.WithholdingCertificateLine.builder()
                                .detail("JOSE LUIS RUIZ AVILA ASESORIA").concept("Retención por honorarios 10%")
                                .base(new BigDecimal("1000000")).rate(new BigDecimal("10")).value(new BigDecimal("100000")).build(),
                        com.erp.erp_cloud.dto.certificates.WithholdingCertificateLine.builder()
                                .detail("JOSE LUIS RUIZ AVILA ASESORIA").concept("Retención por honorarios 10%")
                                .base(new BigDecimal("1000000")).rate(new BigDecimal("10")).value(new BigDecimal("100000")).build()))
                .signer1Name("LEONOR GARCIA LOPEZ")
                .signer1Role("Representante Legal")
                .signer2Name("MIGUEL ANGEL PULIDO ORTEGA")
                .signer2Role("Contador")
                .build();

        String html = service.renderHtml(data);

        assertThat(html).contains("<td>Retención por honorarios 10%</td>");
        assertThat(html).doesNotContain("RUIZ AVILA ASESORIA");
        assertThat(html).contains("table class=\"layout signatures\"");
        assertThat(html).doesNotContain("display: flex");
        // Both signers inside the SAME table row.
        String row = html.substring(html.indexOf("layout signatures"));
        assertThat(row.indexOf("MIGUEL ANGEL PULIDO ORTEGA")).isLessThan(row.indexOf("</tr>"));
    }
}
