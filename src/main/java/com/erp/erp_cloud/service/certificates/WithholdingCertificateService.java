package com.erp.erp_cloud.service.certificates;

import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateResponseDTO;
import com.erp.erp_cloud.dto.certificates.ThirdPartyOptionDTO;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateData;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateLine;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateParametersDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.TaxCategory;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the "Certificado de Retencion" (2026-11-07) -- see the
 * propuesta doc (claude/propuesta-certificados-retencion-2026-11-07.md)
 * for the full design. Deliberately reuses
 * JournalEntryRepository.findTaxItems() (the same query
 * TaxDetailReportService already relies on) instead of writing a new
 * repository query -- the universe of lines a certificate needs is
 * exactly the universe that report already pulls, just scoped to one
 * third party / one tax year / one tax type (confirmed design decision:
 * one certificate = one tax type, never several consolidated).
 *
 * renderHtml() is the SINGLE function that assembles the final
 * document (user-editable body/footer resolved first, then embedded
 * into the code-controlled skeleton) -- both the /preview endpoint and
 * the /pdf endpoint call it, so the preview the accountant checks is
 * byte-for-byte what gets printed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithholdingCertificateService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(WithholdingCertificateService.class);
    private static final String SKELETON_RESOURCE = "templates/certificates/certificate-skeleton.html";
    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();
    private static final DateTimeFormatter ISSUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CO"));

    private final JournalEntryRepository journalEntryRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final TaxRepository taxRepository;
    private final LegalDocumentTemplateService templateService;

    // ─────────────────────────────────────────────────────────
    // Parameters (dropdowns)
    // ─────────────────────────────────────────────────────────

    public WithholdingCertificateParametersDTO getParameters(int taxYear, String taxType) {
        List<String> taxTypes = getAvailableRetentionTaxTypes();
        List<ThirdPartyOptionDTO> thirdParties = (taxType != null && !taxType.isBlank())
                ? getThirdPartiesWithRetentionInYear(taxYear, taxType)
                : List.of();
        return WithholdingCertificateParametersDTO.builder()
                .taxTypes(taxTypes)
                .thirdParties(thirdParties)
                .build();
    }

    private List<String> getAvailableRetentionTaxTypes() {
        Long companyId = currentTenantId();
        return taxRepository.findByCompanyIdOrderByCodeAsc(companyId).stream()
                .filter(tax -> tax.getCategory() == TaxCategory.RETENCION)
                .map(Tax::getType)
                .distinct()
                .sorted()
                .toList();
    }

    private List<ThirdPartyOptionDTO> getThirdPartiesWithRetentionInYear(int taxYear, String taxType) {
        validateTaxYear(taxYear);
        Long companyId = currentTenantId();
        String typeFilter = normalizeType(taxType);
        List<JournalEntryItem> items = journalEntryRepository.findTaxItems(
                companyId, TaxCategory.RETENCION, yearStart(taxYear), yearEnd(taxYear), typeFilter, null);

        Map<String, String> nameByDocument = new LinkedHashMap<>();
        for (JournalEntryItem item : items) {
            ThirdParty thirdParty = item.getThirdParty();
            if (thirdParty != null) {
                nameByDocument.putIfAbsent(thirdParty.getDocumentNumber(), thirdParty.getLegalDisplayName());
            }
        }
        return nameByDocument.entrySet().stream()
                .map(entry -> ThirdPartyOptionDTO.builder()
                        .documentNumber(entry.getKey())
                        .name(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(ThirdPartyOptionDTO::getName))
                .toList();
    }

    // ─────────────────────────────────────────────────────────
    // Certificate data
    // ─────────────────────────────────────────────────────────

    public WithholdingCertificateData getCertificateData(int taxYear, String taxType, String thirdPartyDocument) {
        validateTaxYear(taxYear);
        if (taxType == null || taxType.isBlank()) {
            throw new InvalidOperationException("Tax type is required.", "TAX_TYPE_REQUIRED");
        }
        if (thirdPartyDocument == null || thirdPartyDocument.isBlank()) {
            throw new InvalidOperationException("Third party is required.", "THIRD_PARTY_REQUIRED");
        }

        Long companyId = currentTenantId();
        Company company = currentCompany();
        String typeFilter = normalizeType(taxType);

        ThirdParty thirdParty = thirdPartyRepository
                .findByCompanyIdAndDocumentNumber(companyId, thirdPartyDocument.trim())
                .orElseThrow(() -> new InvalidOperationException("Third party not found.", "THIRD_PARTY_NOT_FOUND"));

        List<JournalEntryItem> items = journalEntryRepository.findTaxItems(
                companyId, TaxCategory.RETENCION, yearStart(taxYear), yearEnd(taxYear),
                typeFilter, thirdParty.getDocumentNumber());

        if (items.isEmpty()) {
            throw new InvalidOperationException(
                    "No retention movements found for this third party, year and tax type.",
                    "NO_RETENTION_MOVEMENTS");
        }

        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalWithheld = BigDecimal.ZERO;
        List<WithholdingCertificateLine> lines = new ArrayList<>();
        // Agrupación por concepto, en el orden en que aparecen.
        java.util.Map<String, WithholdingCertificateLine> byConcept = new java.util.LinkedHashMap<>();
        for (JournalEntryItem item : items) {
            BigDecimal debit = nz(item.getDebit());
            BigDecimal credit = nz(item.getCredit());
            // Same rule as TaxDetailReportService.toLine(): the withheld
            // value is whichever side handleTax() actually populated.
            BigDecimal value = debit.compareTo(BigDecimal.ZERO) != 0 ? debit : credit;
            BigDecimal base = nz(item.getTaxBase());

            totalBase = totalBase.add(base);
            totalWithheld = totalWithheld.add(value);

            String concept = conceptOf(item.getTax());
            BigDecimal rate = item.getTax().getRate();

            lines.add(WithholdingCertificateLine.builder()
                    .entryDate(item.getJournalEntry().getEntryDate().toString())
                    .documentNumber(item.getJournalEntry().getDocumentNumber())
                    .detail(item.getDescription() != null ? item.getDescription() : item.getJournalEntry().getDescription())
                    .concept(concept)
                    .base(base)
                    .rate(rate)
                    .value(value)
                    .build());

            String key = concept + "|" + (rate != null ? rate.stripTrailingZeros().toPlainString() : "");
            WithholdingCertificateLine group = byConcept.get(key);
            if (group == null) {
                byConcept.put(key, WithholdingCertificateLine.builder()
                        .concept(concept).detail(concept).rate(rate).base(base).value(value).build());
            } else {
                group.setBase(group.getBase().add(base));
                group.setValue(group.getValue().add(value));
            }
        }

        String issueCity = company.getCity() != null ? company.getCity().getName() : null;

        return WithholdingCertificateData.builder()
                .companyName(company.getLegalName())
                .companyNit(formatNit(company.getTaxId(), company.getVerificationDigit()))
                .companyAddress(company.getAddress())
                .companyCity(issueCity)
                .thirdPartyName(thirdParty.getLegalDisplayName())
                .thirdPartyNit(formatNit(thirdParty.getDocumentNumber(),
                        thirdParty.getVerificationDigit() != null ? thirdParty.getVerificationDigit().toString() : null))
                .taxYear(taxYear)
                .taxType(typeFilter)
                .taxTypeLabel(typeFilter)
                .totalBase(totalBase)
                .totalWithheld(totalWithheld)
                .lines(lines)
                .conceptLines(new ArrayList<>(byConcept.values()))
                .issueDate(LocalDate.now().format(ISSUE_DATE_FORMAT))
                .issueCity(issueCity)
                .companyLogoDataUrl(company.getLogoDataUrl())
                .signer1Name(company.getSigner1Name())
                .signer1Role(company.getSigner1Role())
                .signer1IdNumber(company.getSigner1IdNumber())
                .signer1ProfessionalLicense(company.getSigner1ProfessionalLicense())
                .signer2Name(company.getSigner2Name())
                .signer2Role(company.getSigner2Role())
                .signer2IdNumber(company.getSigner2IdNumber())
                .signer2ProfessionalLicense(company.getSigner2ProfessionalLicense())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // Bulk ZIP generation (2026-11-09) -- see
    // claude/analisis-generacion-masiva-certificados-retencion-2026-11-09.md.
    // Deliberately NOT a Spring StreamingResponseBody: that type is
    // dispatched onto a separate async task-execution thread by
    // WebAsyncManager, but TenantContext (security.context) is a plain
    // ThreadLocal that TenantFilter clears in its own finally block as
    // soon as this controller method returns -- which happens almost
    // immediately once an async dispatch is handed off. Reading
    // currentTenantId()/currentCompany() from that other thread would
    // either throw ("No tenant ID bound to current thread") or, worse,
    // intermittently read whatever another pooled request last bound
    // there. Writing straight to the servlet response's OutputStream
    // from inside this synchronous @Transactional method keeps every
    // lookup on the one request thread TenantFilter actually set up,
    // for the exact same reason Company.city had to become EAGER
    // rather than lazily reloaded on a different thread/session.
    // ─────────────────────────────────────────────────────────

    /**
     * Validates the request and resolves the final list of third-party
     * document numbers to include, BEFORE anything is written to the
     * response. Split out from writeZip() on purpose: once the zip
     * stream starts, response headers are already committed, so a
     * validation error at that point could no longer come back as a
     * clean JSON error -- the client would just get a truncated/corrupt
     * .zip. Calling this first keeps TAX_TYPE_REQUIRED/THIRD_PARTY_REQUIRED
     * behaving like every other error in the app.
     */
    public List<String> resolveZipDocuments(int taxYear, String taxType, List<String> requestedDocuments) {
        validateTaxYear(taxYear);
        if (taxType == null || taxType.isBlank()) {
            throw new InvalidOperationException("Tax type is required.", "TAX_TYPE_REQUIRED");
        }
        List<String> documents = requestedDocuments == null
                ? List.of()
                : requestedDocuments.stream()
                        .filter(doc -> doc != null && !doc.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (documents.isEmpty()) {
            throw new InvalidOperationException("Select at least one third party.", "THIRD_PARTY_REQUIRED");
        }
        return documents;
    }

    /**
     * Streams one certificate PDF per document into a single .zip,
     * written directly to `out` as each one is generated -- never all
     * of them buffered in memory at once, and the client's download
     * starts progressing immediately instead of only after the last
     * certificate renders. A third party that unexpectedly ends up with
     * no data at generation time (should not happen: resolveZipDocuments()
     * is given the exact list /parameters just produced from the same
     * company/year/type filter, in the same read-only transaction) is
     * skipped with a warning instead of aborting the whole batch for
     * everyone else already written.
     */
    public void writeZip(int taxYear, String taxType, List<String> thirdPartyDocuments, OutputStream out)
            throws IOException {
        String typeFilter = normalizeType(taxType);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (String document : thirdPartyDocuments) {
                try {
                    WithholdingCertificateData data = getCertificateData(taxYear, typeFilter, document);
                    String html = renderHtml(data);
                    byte[] pdf = renderPdf(html);

                    String entryName = "certificado-retencion-" + sanitize(document) + "-"
                            + sanitize(typeFilter) + "-" + taxYear + ".pdf";
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(pdf);
                    zos.closeEntry();
                    zos.flush();
                } catch (InvalidOperationException skipped) {
                    log.warn("Skipping withholding certificate for third party {} while building zip ({}/{}): {}",
                            document, typeFilter, taxYear, skipped.getMessage());
                }
            }
        }
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9-]", "");
    }

    // ─────────────────────────────────────────────────────────
    // Rendering -- single source of truth for preview AND pdf
    // ─────────────────────────────────────────────────────────

    public String renderHtml(WithholdingCertificateData data) {
        LegalDocumentTemplateResponseDTO template =
                templateService.getOrDefault(LegalDocumentTemplateService.WITHHOLDING_CERTIFICATE_KEY);

        Map<String, Object> variables = buildVariableMap(data);

        // Stage 1: resolve the accountant-edited body/footer against the
        // plain variables. Stage 2: embed the ALREADY-RESOLVED strings
        // (via {{{...}}}, unescaped) into the code-controlled skeleton --
        // this is why the skeleton render below does not re-expose the
        // raw body/footer templates to a second substitution pass.
        String resolvedBody = renderMustache(template.getBodyHtml(), variables);
        String resolvedFooter = template.getFooterHtml() != null
                ? renderMustache(template.getFooterHtml(), variables)
                : "";

        // FIX (2026-11-09): document_title used to be inserted RAW into
        // skeletonScope, never run through renderMustache() like body/
        // footer are -- so an admin typing "{{tax_type_label}}" into
        // "Titulo del documento" would have printed those literal
        // braces instead of substituting. Title now gets the exact same
        // Stage-1-then-embed treatment, which is what the new default
        // title (see LegalDocumentTemplateService.builtInDefault())
        // actually relies on.
        String resolvedTitle = renderMustache(template.getDocumentTitle(), variables);

        Map<String, Object> skeletonScope = new HashMap<>(variables);
        skeletonScope.put("resolved_document_title", resolvedTitle);
        skeletonScope.put("resolved_body", resolvedBody);
        skeletonScope.put("resolved_footer", resolvedFooter);

        String skeletonTemplate = ClasspathTemplateLoader.readUtf8(SKELETON_RESOURCE);
        String html = renderMustache(skeletonTemplate, skeletonScope);

        // FIX (2026-11-09): sanitize stray named HTML entities (&ntilde;,
        // &oacute;, etc.) before handing the HTML to openhtmltopdf's
        // strict XML parser -- see claude/progreso-2026-11-09.md. The
        // skeleton and the built-in default body/footer were already
        // corrected to use literal UTF-8 characters, but body/footer/
        // title can also come from a company's OWN saved row in
        // legal_document_templates (free text an admin typed via
        // "Plantillas Legales"), which this fix cannot reach or
        // guarantee is clean -- a row saved before today, or any future
        // admin who copy-pastes text containing HTML entities, would
        // otherwise keep crashing every certificate for that company
        // with a SAXParseException ("entity ... was referenced, but not
        // declared"). Running this on the final assembled HTML, not
        // just the admin-edited pieces, covers the skeleton's own
        // static text too, for free, and keeps /preview and /pdf
        // showing exactly the same characters.
        return sanitizeNamedHtmlEntities(html);
    }

    public byte[] renderPdf(String html) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render withholding certificate PDF.", e);
        }
    }

    private String renderMustache(String template, Map<String, Object> scope) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Mustache mustache = MUSTACHE_FACTORY.compile(new StringReader(template), "withholding-certificate");
        StringWriter writer = new StringWriter();
        try {
            mustache.execute(writer, scope).flush();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render legal document template.", e);
        }
        return writer.toString();
    }

    private static final Pattern NAMED_HTML_ENTITY = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]{1,31});");
    // The 5 entities XML already understands on its own -- left exactly
    // as-is, never decoded back to a literal &/</>/"/', which would
    // reintroduce a character that needs escaping into text content.
    private static final Set<String> XML_BUILTIN_ENTITIES = Set.of("amp", "lt", "gt", "quot", "apos");

    private String sanitizeNamedHtmlEntities(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher matcher = NAMED_HTML_ENTITY.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            String name = matcher.group(1);
            String replacement = XML_BUILTIN_ENTITIES.contains(name) ? token : HtmlUtils.htmlUnescape(token);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, Object> buildVariableMap(WithholdingCertificateData data) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("company_name", data.getCompanyName());
        vars.put("company_nit", data.getCompanyNit());
        vars.put("company_address", data.getCompanyAddress());
        vars.put("company_city", data.getCompanyCity());
        vars.put("third_party_name", data.getThirdPartyName());
        vars.put("third_party_nit", data.getThirdPartyNit());
        vars.put("nit", data.getThirdPartyNit());
        vars.put("tax_year", data.getTaxYear());
        vars.put("tax_type", data.getTaxType());
        vars.put("tax_type_label", data.getTaxTypeLabel());
        vars.put("total_base", formatMoney(data.getTotalBase()));
        vars.put("total_withheld", formatMoney(data.getTotalWithheld()));
        vars.put("issue_date", data.getIssueDate());
        vars.put("issue_city", data.getIssueCity());

        List<WithholdingCertificateLine> lines = data.getLines() != null ? data.getLines() : List.of();
        vars.put("has_lines", !lines.isEmpty());
        vars.put("lines", lines.stream().map(this::lineToMap).toList());
        List<WithholdingCertificateLine> conceptLines =
                data.getConceptLines() != null ? data.getConceptLines() : groupByConcept(lines);
        vars.put("has_concept_lines", !conceptLines.isEmpty());
        vars.put("concept_lines", conceptLines.stream().map(this::lineToMap).toList());

        // NEW (2026-11-09): logo + up to two named signers, see
        // claude/progreso-2026-11-09.md. has_signerN/has_signerN_* are
        // computed HERE, as explicit booleans, same reason has_lines
        // already is -- rather than relying on {{#signer1_id_number}}
        // to treat a blank-but-non-null String as falsy (CompanyService
        // already normalizes blank input to null via blankToNull(), but
        // computing the boolean explicitly removes any doubt either
        // way, instead of depending on exactly how mustache.java
        // evaluates a section guard). has_any_signer drives the
        // skeleton's fallback to the old generic unlabeled signature
        // lines when neither slot is configured.
        boolean hasLogo = data.getCompanyLogoDataUrl() != null && !data.getCompanyLogoDataUrl().isBlank();
        boolean hasSigner1 = isPresent(data.getSigner1Name());
        boolean hasSigner2 = isPresent(data.getSigner2Name());
        vars.put("has_logo", hasLogo);
        vars.put("company_logo", hasLogo ? data.getCompanyLogoDataUrl() : "");
        vars.put("has_signer1", hasSigner1);
        vars.put("signer1_name", data.getSigner1Name());
        vars.put("signer1_role", data.getSigner1Role());
        vars.put("has_signer1_id", isPresent(data.getSigner1IdNumber()));
        vars.put("signer1_id_number", data.getSigner1IdNumber());
        vars.put("has_signer1_license", isPresent(data.getSigner1ProfessionalLicense()));
        vars.put("signer1_professional_license", data.getSigner1ProfessionalLicense());
        vars.put("has_signer2", hasSigner2);
        vars.put("signer2_name", data.getSigner2Name());
        vars.put("signer2_role", data.getSigner2Role());
        vars.put("has_signer2_id", isPresent(data.getSigner2IdNumber()));
        vars.put("signer2_id_number", data.getSigner2IdNumber());
        vars.put("has_signer2_license", isPresent(data.getSigner2ProfessionalLicense()));
        vars.put("signer2_professional_license", data.getSigner2ProfessionalLicense());
        vars.put("has_any_signer", hasSigner1 || hasSigner2);
        return vars;
    }

    // Agrupa renglones por concepto + tarifa (para datos armados sin
    // conceptLines, p. ej. en pruebas o integraciones futuras).
    private List<WithholdingCertificateLine> groupByConcept(List<WithholdingCertificateLine> lines) {
        java.util.Map<String, WithholdingCertificateLine> groups = new java.util.LinkedHashMap<>();
        for (WithholdingCertificateLine l : lines) {
            String concept = l.getConcept() != null ? l.getConcept() : (l.getDetail() != null ? l.getDetail() : "");
            String key = concept + "|" + (l.getRate() != null ? l.getRate().stripTrailingZeros().toPlainString() : "");
            WithholdingCertificateLine g = groups.get(key);
            if (g == null) {
                groups.put(key, WithholdingCertificateLine.builder().concept(concept).detail(concept)
                        .rate(l.getRate()).base(nz(l.getBase())).value(nz(l.getValue())).build());
            } else {
                g.setBase(g.getBase().add(nz(l.getBase())));
                g.setValue(g.getValue().add(nz(l.getValue())));
            }
        }
        return new ArrayList<>(groups.values());
    }

    // Concepto legible de la retención: el nombre que el contador le dio al
    // impuesto en el catálogo; si no tiene, su código o tipo.
    private String conceptOf(com.erp.erp_cloud.entity.Tax tax) {
        if (tax == null) return "";
        if (isPresent(tax.getName())) return tax.getName().trim();
        if (isPresent(tax.getCode())) return tax.getCode().trim();
        return tax.getType() != null ? tax.getType() : "";
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> lineToMap(WithholdingCertificateLine line) {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_date", line.getEntryDate());
        map.put("document_number", line.getDocumentNumber());
        map.put("detail", line.getDetail());
        map.put("concept", line.getConcept() != null ? line.getConcept() : line.getDetail());
        map.put("base", formatMoney(line.getBase()));
        map.put("rate", line.getRate() != null ? line.getRate().stripTrailingZeros().toPlainString() : "");
        map.put("value", formatMoney(line.getValue()));
        return map;
    }

    // ─────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────

    private void validateTaxYear(int taxYear) {
        int currentYear = LocalDate.now().getYear();
        if (taxYear < 2000 || taxYear > currentYear) {
            throw new InvalidOperationException(
                    "Tax year must be between 2000 and the current year.", "INVALID_TAX_YEAR");
        }
    }

    private String normalizeType(String taxType) {
        return taxType.trim().toUpperCase();
    }

    private LocalDate yearStart(int taxYear) {
        return LocalDate.of(taxYear, 1, 1);
    }

    private LocalDate yearEnd(int taxYear) {
        return LocalDate.of(taxYear, 12, 31);
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal rounded = nz(value).setScale(0, RoundingMode.HALF_UP);
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "CO"));
        return format.format(rounded);
    }

    private String formatNit(String number, String checkDigit) {
        if (number == null) {
            return "";
        }
        return (checkDigit != null && !checkDigit.isBlank()) ? number + "-" + checkDigit : number;
    }
}
