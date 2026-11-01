package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.SiewinThirdPartyImportRequest;
import com.erp.erp_cloud.dto.SiewinThirdPartyImportResultDTO;
import com.erp.erp_cloud.dto.SiewinThirdPartyRowDTO;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.IdentificationDocumentType;
import com.erp.erp_cloud.enums.TaxRegime;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.CityRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Importer for a client's legacy third parties coming from SIEWIN
 * (t_terceros), migrating them into real ThirdParty rows for the current
 * tenant.
 *
 * Full analysis and the decisions behind every choice below are in two
 * project docs: propuesta-importador-terceros-siewin-2026-10-31.md (the
 * import design itself, 5 decisions approved 2026-10-31) and
 * propuesta-enum-tipo-documento-dian-2026-11-01.md (the IdentificationDocumentType
 * enum this service maps into, 3 decisions approved 2026-11-01). Summary
 * of what was approved and why:
 *
 * 1. PERSON TYPE DERIVATION -- the source "0=P.JURIDICA,1=NATURAL" flag
 *    is IGNORED entirely: verified against the real 320-row sample, it
 *    contradicted the actual name fields in 58% of rows (187/320). What
 *    IS reliable in that same sample is a perfect partition: razonSocial
 *    is populated in exactly the rows where no name field is, and vice
 *    versa (0 rows with both, 0 rows with neither). personType is
 *    derived from that instead -- see derivePersonType().
 *
 * 2. NAME MAPPING -- no concatenation. ThirdParty already has one column
 *    per SIEWIN name column (firstName/middleName/lastName/
 *    secondLastName/businessName) -- straight field-to-field copy.
 *
 * 3. VERIFICATION DIGIT -- never imported, never compared. This service
 *    does not even read a "D.V." column (SiewinThirdPartyRowDTO has no
 *    such field) -- ThirdPartyService's own calculateDV() recomputes it
 *    authoritatively for any numeric document whenever a ThirdParty is
 *    saved through the normal entity lifecycle; this importer saves
 *    through the same repository, so that still applies on read, but
 *    this service does not duplicate that calculation here on write --
 *    it simply never sets verificationDigit, same end result.
 *
 * 4. TAX REGIME -- no source column exists for this in the file actually
 *    provided (t_regimen/facturar_con_iva/t_gran_contribuyente/
 *    t_autoretenedor from the full t_terceros DDL are absent from this
 *    client's export). Approved default (Option A, 2026-11-01): CORPORATE
 *    for JURIDICA, INDIVIDUAL for NATURAL -- the first/canonical "regimen
 *    ordinario" option for each person type, same default
 *    getDefaultTaxRegimeForPersonType() uses on the frontend. Adjustable
 *    by hand afterwards from the Terceros screen.
 *
 * 5. DOCUMENT TYPE -- mapped through SIEWIN_DOCUMENT_TYPE_MAP (0=CC,
 *    1=NIT, 2=CE, 3=TI, matching IdentificationDocumentType's DIAN
 *    codes). Any other value is Fail-Fast (unmappedDocumentTypes) --
 *    the real sample only ever uses 0 and 1, so this does not change
 *    the outcome for Navarra, only the robustness for a future file.
 *
 * 6. MUNICIPALITY -- codMunicipio is zero-padded to 5 digits (DANE codes
 *    lose their leading zero once read as a number, e.g. Excel/pandas
 *    reads "05001" as 5001) and resolved against CityRepository.findByCode()
 *    (added for this importer -- City only had findByNameContainingIgnoreCase
 *    before). The precargada catalog itself
 *    (sql/2026-10-31_populate_cities_municipios.sql) is a separate,
 *    already-executed, non-code-gated deliverable -- not part of this
 *    service.
 *
 * 7. COUNTRY -- out of scope by design. The source "COD.PAIS" column is
 *    not even in SiewinThirdPartyRowDTO (no t_paises reference table was
 *    provided to decode it, and ThirdParty has no country field -- it is
 *    already implied through city -> country once the municipality
 *    resolves).
 *
 * 8. FAIL-FAST -- same "collect every violation across the whole file,
 *    throw ONE exception" pattern as SiewinPucImportService: every row is
 *    checked before anything is created, so a bad source file is fully
 *    diagnosed in a single round trip (SIEWIN_THIRD_PARTY_INTEGRITY_GAP).
 *    Deliberately NOT reusing ThirdPartyService.createBulk() -- that
 *    method already exists but aborts on the FIRST duplicate it finds;
 *    it does not accumulate violations the way this importer needs to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SiewinThirdPartyImportService extends TenantAwareService {

    private final ThirdPartyRepository thirdPartyRepository;
    private final CityRepository cityRepository;
    private final CompanyRepository companyRepository;

    // 0=CC, 1=NIT, 2=C.EXTR, 3=T.I. -- the only 4 codes this client's
    // export header defines. Any other value is Fail-Fast
    // (unmappedDocumentTypes), never guessed.
    private static final Map<Integer, IdentificationDocumentType> SIEWIN_DOCUMENT_TYPE_MAP = Map.of(
            0, IdentificationDocumentType.CC,
            1, IdentificationDocumentType.NIT,
            2, IdentificationDocumentType.CE,
            3, IdentificationDocumentType.TI
    );

    // Source placeholder values meaning "no real phone number" (see
    // proposal section 3, "Hallazgos no bloqueantes") -- 14/320 rows
    // (4%) in the real sample had one of these instead of a number.
    private static final Set<String> PHONE_PLACEHOLDER_VALUES = Set.of("*", "0");

    public SiewinThirdPartyImportResultDTO importFromSiewin(SiewinThirdPartyImportRequest request) {
        Long companyId = currentTenantId();
        companyRepository.findById(companyId)
                .orElseThrow(() -> new InvalidOperationException(
                        "Company not found for the current session.", "COMPANY_NOT_FOUND"));

        List<SiewinThirdPartyRowDTO> rows = request == null ? null : request.rows();
        if (rows == null || rows.isEmpty()) {
            throw new InvalidOperationException(
                    "El archivo no contiene filas para importar.", "SIEWIN_EMPTY_FILE");
        }

        // ── Pass 1: per-row derivation + validation ──────────────────────
        List<String> missingDocumentNumber = new ArrayList<>();
        List<String> duplicateInBatch = new ArrayList<>();
        List<String> duplicateInCompany = new ArrayList<>();
        List<String> unmappedDocumentTypes = new ArrayList<>();
        List<String> ambiguousPersonType = new ArrayList<>();
        List<String> municipalityNotFound = new ArrayList<>();

        Set<String> seenDocumentNumbers = new HashSet<>();
        List<DerivedRow> derivedRows = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            SiewinThirdPartyRowDTO row = rows.get(i);
            String label = rowLabel(row, i);

            String documentNumber = row.documentNumber() == null ? null : row.documentNumber().trim();
            if (documentNumber == null || documentNumber.isEmpty()) {
                missingDocumentNumber.add(label);
                continue;
            }

            if (!seenDocumentNumbers.add(documentNumber)) {
                duplicateInBatch.add(label + " (" + documentNumber + ")");
                continue;
            }
            if (thirdPartyRepository.existsByCompanyIdAndDocumentNumber(companyId, documentNumber)) {
                duplicateInCompany.add(label + " (" + documentNumber + ")");
                continue;
            }

            // Map.of(...).get(null) is undefined-ish behavior to rely on
            // (some JDK immutable-map implementations NPE on a null key
            // via hashCode()) -- a row with a blank/missing document-type
            // cell is plausible real-world dirty data, not a hypothetical,
            // so this is checked explicitly rather than trusted to the map.
            Integer documentTypeCode = row.documentTypeCode();
            IdentificationDocumentType documentType = documentTypeCode == null
                    ? null : SIEWIN_DOCUMENT_TYPE_MAP.get(documentTypeCode);
            if (documentType == null) {
                unmappedDocumentTypes.add(label + " (" + documentNumber + ", codigo="
                        + row.documentTypeCode() + ")");
                continue;
            }

            String personType = derivePersonType(row);
            if (personType == null) {
                ambiguousPersonType.add(label + " (" + documentNumber + ")");
                continue;
            }

            String municipalityCode = row.codMunicipio() == null
                    ? null : String.format("%05d", row.codMunicipio());
            City city = municipalityCode == null
                    ? null : cityRepository.findByCode(municipalityCode).orElse(null);
            if (city == null) {
                municipalityNotFound.add(label + " (" + documentNumber + ", municipio="
                        + (municipalityCode == null ? "sin dato" : municipalityCode) + ")");
                continue;
            }

            derivedRows.add(new DerivedRow(row, documentNumber, documentType, personType, city));
        }

        if (!missingDocumentNumber.isEmpty() || !duplicateInBatch.isEmpty() || !duplicateInCompany.isEmpty()
                || !unmappedDocumentTypes.isEmpty() || !ambiguousPersonType.isEmpty()
                || !municipalityNotFound.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("missingDocumentNumber", missingDocumentNumber);
            details.put("duplicateInBatch", duplicateInBatch);
            details.put("duplicateInCompany", duplicateInCompany);
            details.put("unmappedDocumentTypes", unmappedDocumentTypes);
            details.put("ambiguousPersonType", ambiguousPersonType);
            details.put("municipalityNotFound", municipalityNotFound);

            log.error("IMPORT_SIEWIN_THIRD_PARTY_INTEGRITY_GAP | tenant: {} | details: {}", companyId, details);
            throw new InvalidOperationException(
                    "El archivo de terceros SIEWIN tiene problemas que impiden la importacion. Revise los detalles.",
                    "SIEWIN_THIRD_PARTY_INTEGRITY_GAP", details);
        }

        // ── Everything validated -- create every real row ────────────────
        List<String> phonePlaceholderWarnings = new ArrayList<>();
        int created = 0;
        for (DerivedRow d : derivedRows) {
            ThirdParty entity = new ThirdParty();
            entity.setCompany(companyRepository.getReferenceById(companyId));
            entity.setActive(true);
            entity.setDocumentNumber(d.documentNumber());
            entity.setDocumentType(d.documentType());
            entity.setPersonType(d.personType());
            entity.setTaxRegime("JURIDICA".equals(d.personType()) ? TaxRegime.CORPORATE : TaxRegime.INDIVIDUAL);
            entity.setCity(d.city());

            if ("JURIDICA".equals(d.personType())) {
                entity.setBusinessName(upper(d.row().razonSocial()));
            } else {
                entity.setFirstName(upper(d.row().primerNombre()));
                entity.setMiddleName(upper(d.row().segundoNombre()));
                entity.setLastName(upper(d.row().primerApellido()));
                entity.setSecondLastName(upper(d.row().segundoApellido()));
            }

            entity.setAddress(upper(d.row().direccion()));

            String rawPhone = d.row().telefono();
            String phone = normalizePhone(rawPhone);
            entity.setPhone(phone);
            if (phone == null && hasText(rawPhone)) {
                phonePlaceholderWarnings.add(d.documentNumber() + " - \"" + rawPhone.trim() + "\"");
            }

            thirdPartyRepository.save(entity);
            created++;
        }

        log.info("IMPORT_SIEWIN_THIRD_PARTY_DONE | tenant: {} | thirdPartiesCreated: {} | phonePlaceholderWarnings: {}",
                companyId, created, phonePlaceholderWarnings.size());

        return new SiewinThirdPartyImportResultDTO(created, phonePlaceholderWarnings);
    }

    /**
     * Section 1 of the proposal: razonSocial present and no personal name
     * -> JURIDICA; a personal name present and no razonSocial -> NATURAL;
     * both or neither populated is ambiguous (Fail-Fast) -- never
     * guessed, even though 0/320 rows hit this case in the real sample.
     */
    private String derivePersonType(SiewinThirdPartyRowDTO row) {
        boolean hasBusinessName = hasText(row.razonSocial());
        boolean hasPersonName = hasText(row.primerApellido()) || hasText(row.primerNombre());

        if (hasBusinessName && !hasPersonName) {
            return "JURIDICA";
        }
        if (hasPersonName && !hasBusinessName) {
            return "NATURAL";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String upper(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase();
    }

    /** Null (no real number) for an empty value or one of PHONE_PLACEHOLDER_VALUES. */
    private String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || PHONE_PLACEHOLDER_VALUES.contains(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String rowLabel(SiewinThirdPartyRowDTO row, int index) {
        return "fila " + (index + 1);
    }

    private record DerivedRow(SiewinThirdPartyRowDTO row, String documentNumber,
                               IdentificationDocumentType documentType, String personType, City city) {
    }
}
