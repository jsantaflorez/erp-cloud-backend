package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.dto.DocumentTypeResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.DocumentTypeRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for DocumentTypeService's business rules -- no Spring
 * context, no database. Mirrors checklist-pruebas-erp.md under "Tipos de
 * Documento": consecutive locked on edit, dedicated reset action rejects
 * lowering the consecutive, and activate/deactivate both directions.
 *
 * Runs via `./gradlew test` (no live MySQL needed).
 */
class DocumentTypeServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    @Mock private DocumentTypeRepository repository;
    @Mock private ChartOfAccountsRepository accountRepository;
    @Mock private CompanyRepository companyRepository;

    private DocumentTypeService service;
    private Company testCompany;
    private ChartOfAccounts defaultAccount;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);

        service = new DocumentTypeService(repository, accountRepository, companyRepository);

        testCompany = new Company();
        testCompany.setId(COMPANY_ID);

        defaultAccount = new ChartOfAccounts();
        defaultAccount.setId(ACCOUNT_ID);
        defaultAccount.setCode("510501");
        defaultAccount.setCompany(testCompany);

        when(companyRepository.getReferenceById(COMPANY_ID)).thenReturn(testCompany);
        when(repository.save(any(DocumentType.class)))
                .thenAnswer(invocation -> {
                    DocumentType dt = invocation.getArgument(0);
                    if (dt.getId() == null) dt.setId(100L);
                    return dt;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DocumentTypeRequest baseRequest(String code) {
        DocumentTypeRequest req = new DocumentTypeRequest();
        req.setCode(code);
        req.setName("Factura de Venta");
        req.setPrefix("FV");
        req.setIsAccounting(true);
        return req;
    }

    // ============================================================
    // Create
    // ============================================================

    @Test
    @DisplayName("create() binds company, sets active status, and assigns persistent state")
    void create_bindsCompanyAndSetsActive() {
        service.create(baseRequest("FV"));

        ArgumentCaptor<DocumentType> captor = ArgumentCaptor.forClass(DocumentType.class);
        verify(repository).save(captor.capture());

        DocumentType saved = captor.getValue();
        assertThat(saved.getCompany()).isEqualTo(testCompany);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("create() honors an explicit initial consecutive from the request")
    void create_honorsRequestedInitialConsecutive() {
        DocumentTypeRequest request = baseRequest("FV");
        request.setCurrentConsecutive(50L);

        DocumentTypeResponseDTO result = service.create(request);

        assertThat(result.getCurrentConsecutive()).isEqualTo(50L);
    }

    @Test
    @DisplayName("create() defaults the consecutive to 0 when the request omits it")
    void create_defaultsConsecutiveToZero_whenOmitted() {
        DocumentTypeResponseDTO result = service.create(baseRequest("FV"));

        assertThat(result.getCurrentConsecutive()).isZero();
    }

    @Test
    @DisplayName("create() rejects a duplicate code within the same company")
    void create_duplicateCode_throws() {
        when(repository.existsByCompanyIdAndCode(COMPANY_ID, "FV")).thenReturn(true);

        assertThatThrownBy(() -> service.create(baseRequest("FV")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create() with a default account binds it when it belongs to the same company")
    void create_withDefaultAccount_success() {
        DocumentTypeRequest request = baseRequest("FV");
        request.setDefaultAccountId(ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(defaultAccount));

        DocumentTypeResponseDTO result = service.create(request);

        assertThat(result.getDefaultAccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    @DisplayName("create() rejects a default account that belongs to a different company")
    void create_defaultAccountFromDifferentCompany_throwsResourceNotFound() {
        Company otherCompany = new Company();
        otherCompany.setId(999L);
        defaultAccount.setCompany(otherCompany);

        DocumentTypeRequest request = baseRequest("FV");
        request.setDefaultAccountId(ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(defaultAccount));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create() rejects a non-existent default account")
    void create_unknownDefaultAccount_throwsResourceNotFound() {
        DocumentTypeRequest request = baseRequest("FV");
        request.setDefaultAccountId(ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // Update
    // ============================================================

    @Test
    @DisplayName("update() rejects renaming the code to one already used by another document type")
    void update_codeChangedToExistingCode_throws() {
        DocumentType existing = existingDocType(5L, "FV");
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.existsByCompanyIdAndCode(COMPANY_ID, "CE")).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, baseRequest("CE")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("update() does not re-check duplicates when the code is unchanged")
    void update_codeUnchanged_skipsDuplicateCheck() {
        DocumentType existing = existingDocType(5L, "FV");
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        DocumentTypeResponseDTO result = service.update(5L, baseRequest("FV"));

        assertThat(result.getCode()).isEqualTo("FV");
    }

    @Test
    @DisplayName("update() never touches currentConsecutive -- it is locked, adjusted only via resetConsecutive()")
    void update_doesNotTouchConsecutive() {
        DocumentType existing = existingDocType(5L, "FV");
        existing.setCurrentConsecutive(42L);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        DocumentTypeRequest request = baseRequest("FV");
        request.setCurrentConsecutive(999L); // even if the client sends a value

        DocumentTypeResponseDTO result = service.update(5L, request);

        assertThat(result.getCurrentConsecutive()).isEqualTo(42L);
    }

    // ============================================================
    // Consecutive: getNextConsecutive() / resetConsecutive()
    // ============================================================

    @Test
    @DisplayName("getNextConsecutive() increments and persists the running counter")
    void getNextConsecutive_incrementsAndReturnsNext() {
        DocumentType existing = existingDocType(5L, "FV");
        existing.setCurrentConsecutive(7L);
        when(repository.findByIdWithLock(5L)).thenReturn(Optional.of(existing));

        Long next = service.getNextConsecutive(5L);

        assertThat(next).isEqualTo(8L);
        assertThat(existing.getCurrentConsecutive()).isEqualTo(8L);
    }

    @Test
    @DisplayName("getNextConsecutive() rejects a document type from another company")
    void getNextConsecutive_crossTenant_throwsResourceNotFound() {
        DocumentType existing = existingDocType(5L, "FV");
        Company otherCompany = new Company();
        otherCompany.setId(999L);
        existing.setCompany(otherCompany);
        when(repository.findByIdWithLock(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getNextConsecutive(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("resetConsecutive() rejects a value lower than the current consecutive")
    void resetConsecutive_lowerValue_throwsCannotDecrease() {
        DocumentType existing = existingDocType(5L, "FV");
        existing.setCurrentConsecutive(100L);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resetConsecutive(5L, 99L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot assign a lower consecutive");
    }

    @Test
    @DisplayName("resetConsecutive() allows setting the same value as the current consecutive")
    void resetConsecutive_equalValue_succeeds() {
        DocumentType existing = existingDocType(5L, "FV");
        existing.setCurrentConsecutive(100L);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        service.resetConsecutive(5L, 100L);

        assertThat(existing.getCurrentConsecutive()).isEqualTo(100L);
    }

    @Test
    @DisplayName("resetConsecutive() allows raising the consecutive")
    void resetConsecutive_higherValue_succeeds() {
        DocumentType existing = existingDocType(5L, "FV");
        existing.setCurrentConsecutive(100L);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        service.resetConsecutive(5L, 500L);

        assertThat(existing.getCurrentConsecutive()).isEqualTo(500L);
    }

    // ============================================================
    // Activate / deactivate
    // ============================================================

    @Test
    @DisplayName("deactivate() sets the document type inactive")
    void deactivate_setsInactive() {
        DocumentType existing = existingDocType(5L, "FV");
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        service.deactivate(5L);

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    @DisplayName("activate() sets the document type active")
    void activate_setsActive() {
        DocumentType existing = existingDocType(5L, "FV");
        existing.setActive(false);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        service.activate(5L);

        assertThat(existing.isActive()).isTrue();
    }

    // ============================================================
    // Tenant isolation on read
    // ============================================================

    @Test
    @DisplayName("findByIdDto() rejects a document type from another company")
    void findByIdDto_crossTenant_throwsResourceNotFound() {
        DocumentType existing = existingDocType(5L, "FV");
        Company otherCompany = new Company();
        otherCompany.setId(999L);
        existing.setCompany(otherCompany);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.findByIdDto(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // Response formatting (fullDescription / nextNumberPreview)
    // ============================================================

    @Test
    @DisplayName("create() previews the next number with prefix as PREFIX-N")
    void create_previewsNextNumber_withPrefix() {
        DocumentTypeResponseDTO result = service.create(baseRequest("FV"));

        assertThat(result.getFullDescription()).isEqualTo("FV - Factura de Venta");
        assertThat(result.getNextNumberPreview()).isEqualTo("FV-1");
    }

    @Test
    @DisplayName("create() previews the next number without prefix as just N")
    void create_previewsNextNumber_withoutPrefix() {
        DocumentTypeRequest request = baseRequest("CE");
        request.setPrefix(null);

        DocumentTypeResponseDTO result = service.create(request);

        assertThat(result.getNextNumberPreview()).isEqualTo("1");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private DocumentType existingDocType(Long id, String code) {
        DocumentType dt = new DocumentType();
        dt.setId(id);
        dt.setCode(code);
        dt.setName("Factura de Venta");
        dt.setCompany(testCompany);
        dt.setActive(true);
        dt.setCurrentConsecutive(0L);
        return dt;
    }
}
