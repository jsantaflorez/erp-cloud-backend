package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.TaxRegime;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CityRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for ThirdPartyService's business logic -- no Spring context,
 * no database. Mirrors the manual scenarios in checklist-pruebas-erp.md under
 * "Terceros" so those don't have to be re-verified by hand on every change.
 *
 * Runs in milliseconds via `./gradlew test` (no live MySQL needed). Tests that
 * DO need a live datasource are tagged @Tag("integration") and excluded here.
 */
class ThirdPartyServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Mock private CityRepository cityRepository;
    @Mock private CostCenterRepository costCenterRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private CompanyRepository companyRepository;

    private ThirdPartyService thirdPartyService;
    private Company testCompany;
    private City testCity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);

        thirdPartyService = new ThirdPartyService(
                thirdPartyRepository, cityRepository, costCenterRepository,
                journalEntryRepository, companyRepository
        );

        testCompany = new Company();
        testCompany.setId(COMPANY_ID);

        testCity = new City();
        testCity.setId(1L);
        testCity.setName("Bogota");

        when(companyRepository.getReferenceById(COMPANY_ID)).thenReturn(testCompany);
        when(cityRepository.findById(1L)).thenReturn(Optional.of(testCity));
        when(thirdPartyRepository.save(any(ThirdParty.class)))
                .thenAnswer(invocation -> {
                    ThirdParty tp = invocation.getArgument(0);
                    if (tp.getId() == null) tp.setId(100L);
                    return tp;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ThirdPartyRequest baseNaturalRequest(String documentNumber) {
        ThirdPartyRequest req = new ThirdPartyRequest();
        req.setDocumentNumber(documentNumber);
        req.setDocumentType("CC");
        req.setPersonType("NATURAL");
        req.setTaxRegime(TaxRegime.INDIVIDUAL);
        req.setFirstName("Juan");
        req.setLastName("Perez");
        req.setCityId(1L);
        return req;
    }

    // ============================================================
    // Verification digit (DV) -- checklist bug: "no calcula el DV
    // (los naturales tambien tienen DV)"
    // ============================================================

    @ParameterizedTest(name = "DV({0}) = {1}")
    @DisplayName("create() calculates the Modulo-11 verification digit for any numeric document")
    @CsvSource({
            "900373115, 3",
            "860502609, 1",
            "213368491, 6",
            "800197268, 4",
            "43982571, 3",
            "1, 8"
    })
    void create_calculatesVerificationDigit_forAnyNumericDocument(String documentNumber, int expectedDv) {
        ThirdPartyResponseDTO result = thirdPartyService.create(baseNaturalRequest(documentNumber));

        assertThat(result.getVerificationDigit()).isEqualTo(expectedDv);
    }

    @Test
    @DisplayName("create() ignores whatever DV the client sends and always recalculates it")
    void create_ignoresClientSuppliedVerificationDigit() {
        ThirdPartyRequest request = baseNaturalRequest("900373115");
        request.setVerificationDigit(9); // spoofed/incorrect value from a hypothetical client

        ThirdPartyResponseDTO result = thirdPartyService.create(request);

        assertThat(result.getVerificationDigit()).isEqualTo(3);
    }

    @Test
    @DisplayName("create() leaves the DV null for non-numeric documents (e.g. passport)")
    void create_nonNumericDocument_hasNoVerificationDigit() {
        ThirdPartyRequest request = baseNaturalRequest("AB1234567");
        request.setDocumentType("PP");

        ThirdPartyResponseDTO result = thirdPartyService.create(request);

        assertThat(result.getVerificationDigit()).isNull();
    }

    @Test
    @DisplayName("create() rejects a document number longer than 15 digits")
    void create_documentLongerThan15Digits_throws() {
        ThirdPartyRequest request = baseNaturalRequest("1234567890123456"); // 16 digits

        assertThatThrownBy(() -> thirdPartyService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("15 digits");
    }

    // ============================================================
    // Required fields per person type
    // ============================================================

    @Test
    @DisplayName("create() requires business name for JURIDICA")
    void create_juridica_withoutBusinessName_throws() {
        ThirdPartyRequest request = baseNaturalRequest("900123456");
        request.setPersonType("JURIDICA");
        request.setBusinessName(null);

        assertThatThrownBy(() -> thirdPartyService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Business name");
    }

    @Test
    @DisplayName("create() requires first and last name for NATURAL")
    void create_natural_withoutLastName_throws() {
        ThirdPartyRequest request = baseNaturalRequest("43982571");
        request.setLastName(null);

        assertThatThrownBy(() -> thirdPartyService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("required for natural persons");
    }

    // ============================================================
    // Duplicate / not-found / cost-center validation
    // ============================================================

    @Test
    @DisplayName("create() rejects a document number already used in the same company")
    void create_duplicateDocumentNumberInSameCompany_throws() {
        ThirdPartyRequest request = baseNaturalRequest("900123456");
        when(thirdPartyRepository.existsByCompanyIdAndDocumentNumber(COMPANY_ID, "900123456"))
                .thenReturn(true);

        assertThatThrownBy(() -> thirdPartyService.create(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create() rejects a city that does not exist")
    void create_unknownCity_throwsResourceNotFound() {
        ThirdPartyRequest request = baseNaturalRequest("900123456");
        request.setCityId(999L);
        when(cityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> thirdPartyService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create() rejects a default cost center that does not allow movement")
    void create_costCenterThatDoesNotAllowMovement_throws() {
        ThirdPartyRequest request = baseNaturalRequest("900123456");
        request.setDefaultCostCenterId(5L);

        CostCenter nonPostingCc = new CostCenter();
        nonPostingCc.setId(5L);
        nonPostingCc.setCompany(testCompany);
        nonPostingCc.setAllowsMovement(false);

        when(costCenterRepository.findById(5L)).thenReturn(Optional.of(nonPostingCc));

        assertThatThrownBy(() -> thirdPartyService.create(request))
                .isInstanceOf(InvalidOperationException.class);
    }

    // ============================================================
    // Deactivate rule -- checklist: "no se puede desactivar si tiene
    // movimientos contables"
    // ============================================================

    @Test
    @DisplayName("deactivate() refuses a third party with existing accounting movements")
    void deactivate_thirdPartyWithAccountingMovements_throws() {
        ThirdParty existing = new ThirdParty();
        existing.setId(10L);
        existing.setCompany(testCompany);
        existing.setActive(true);

        when(thirdPartyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(journalEntryRepository.existsByThirdParty(existing)).thenReturn(true);

        assertThatThrownBy(() -> thirdPartyService.deactivate(10L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("accounting movements");
    }

    @Test
    @DisplayName("deactivate() succeeds for a third party without accounting movements")
    void deactivate_thirdPartyWithoutMovements_succeeds() {
        ThirdParty existing = new ThirdParty();
        existing.setId(11L);
        existing.setCompany(testCompany);
        existing.setActive(true);

        when(thirdPartyRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(journalEntryRepository.existsByThirdParty(existing)).thenReturn(false);

        thirdPartyService.deactivate(11L);

        assertThat(existing.getActive()).isFalse();
        verify(thirdPartyRepository).save(existing);
    }
}
