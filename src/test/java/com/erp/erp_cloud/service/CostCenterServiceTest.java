package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CostCenterRequest;
import com.erp.erp_cloud.dto.CostCenterResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for CostCenterService's business rules -- no Spring
 * context, no database. Mirrors checklist-pruebas-erp.md under "Centros de
 * Costo", plus the self-parent/circular-reference guards added alongside
 * these tests (the backend had no equivalent to
 * ChartOfAccountService.isDescendant() until now -- only the frontend
 * filtered the parent selector).
 *
 * Runs via `./gradlew test` (no live MySQL needed).
 */
class CostCenterServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock private CostCenterRepository repository;
    @Mock private CompanyRepository companyRepository;

    private CostCenterService service;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);

        service = new CostCenterService(repository, companyRepository);

        testCompany = new Company();
        testCompany.setId(COMPANY_ID);

        when(companyRepository.getReferenceById(COMPANY_ID)).thenReturn(testCompany);
        when(repository.save(any(CostCenter.class)))
                .thenAnswer(invocation -> {
                    CostCenter cc = invocation.getArgument(0);
                    if (cc.getId() == null) cc.setId(100L);
                    return cc;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CostCenterRequest baseRequest(String code, Long parentId) {
        CostCenterRequest req = new CostCenterRequest();
        req.setCode(code);
        req.setName("Test cost center " + code);
        req.setParentId(parentId);
        req.setAllowsMovement(false);
        req.setActive(true);
        return req;
    }

    private CostCenter existingCostCenter(Long id, String code, Integer level, boolean allowsMovement, CostCenter parent) {
        CostCenter cc = new CostCenter();
        cc.setId(id);
        cc.setCode(code);
        cc.setName("Existing " + code);
        cc.setLevel(level);
        cc.setAllowsMovement(allowsMovement);
        cc.setActive(true);
        cc.setCompany(testCompany);
        cc.setParent(parent);
        return cc;
    }

    // ============================================================
    // Root creation & company binding
    // ============================================================

    @Test
    @DisplayName("create() creates a root cost center (no parent) at level 1")
    void create_root_succeeds() {
        CostCenterResponseDTO result = service.create(baseRequest("CC01", null));

        assertThat(result.getCode()).isEqualTo("CC01");
        assertThat(result.getParentId()).isNull();
    }

    // ============================================================
    // Consistency rule: allowsMovement requires active — checklist item
    // ============================================================

    @Test
    @DisplayName("create() rejects allowsMovement=true on an inactive cost center")
    void create_movementOnInactive_throws() {
        CostCenterRequest request = baseRequest("CC01", null);
        request.setAllowsMovement(true);
        request.setActive(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot allow movements on an inactive");
    }

    // ============================================================
    // Hierarchy rule: an operational (allowsMovement) parent cannot
    // have sub-centers
    // ============================================================

    @Test
    @DisplayName("create() rejects adding a sub-center under a parent that allows movement")
    void create_parentAllowsMovement_throws() {
        CostCenter parent = existingCostCenter(1L, "CC01", 1, true, null);
        when(repository.findById(1L)).thenReturn(Optional.of(parent));

        CostCenterRequest request = baseRequest("CC0101", 1L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("marked to allow movements");
    }

    @Test
    @DisplayName("create() rejects a parent that belongs to a different company")
    void create_parentFromDifferentCompany_throwsResourceNotFound() {
        Company otherCompany = new Company();
        otherCompany.setId(999L);
        CostCenter parent = existingCostCenter(1L, "CC01", 1, false, null);
        parent.setCompany(otherCompany);
        when(repository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.create(baseRequest("CC0101", 1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // NEW: self-parent and circular-reference guards
    // ============================================================

    @Test
    @DisplayName("update() rejects setting a cost center as its own parent")
    void update_selfAsParent_throws() {
        CostCenter existing = existingCostCenter(5L, "CC05", 1, false, null);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        CostCenterRequest request = baseRequest("CC05", 5L); // parentId == own id

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("cannot be its own parent");
    }

    @Test
    @DisplayName("update() rejects moving a cost center under one of its own descendants (circular reference)")
    void update_circularReference_throws() {
        CostCenter grandparent = existingCostCenter(1L, "CC01", 1, false, null);
        CostCenter existing = existingCostCenter(2L, "CC0101", 2, false, grandparent);
        CostCenter descendant = existingCostCenter(3L, "CC010101", 3, false, existing);

        // service.update() looks up "existing" (id=2) by its own id first...
        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        // ...then tries to move it under its own descendant (id=3)
        when(repository.findById(3L)).thenReturn(Optional.of(descendant));

        CostCenterRequest request = baseRequest("CC0101", 3L);

        assertThatThrownBy(() -> service.update(2L, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Circular reference");
    }

    @Test
    @DisplayName("update() allows moving a cost center under an unrelated valid parent")
    void update_moveToUnrelatedParent_succeeds() {
        CostCenter existing = existingCostCenter(2L, "CC02", 1, false, null);
        CostCenter newParent = existingCostCenter(9L, "CC09", 1, false, null);

        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.findById(9L)).thenReturn(Optional.of(newParent));

        CostCenterResponseDTO result = service.update(2L, baseRequest("CC02", 9L));

        assertThat(result.getParentId()).isEqualTo(9L);
    }

    // ============================================================
    // Downward consistency: cannot flip allowsMovement=true if the
    // center already has children
    // ============================================================

    @Test
    @DisplayName("update() rejects marking a cost center with children as allowsMovement=true")
    void update_allowsMovementWithChildren_throws() {
        CostCenter existing = existingCostCenter(5L, "CC05", 1, false, null);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.existsByParentId(5L)).thenReturn(true);

        CostCenterRequest request = baseRequest("CC05", null);
        request.setAllowsMovement(true);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("has sub-centers");
    }

    // ============================================================
    // Deactivate: cannot deactivate with active children
    // ============================================================

    @Test
    @DisplayName("deactivate() refuses a cost center with active sub-centers")
    void deactivate_withActiveChildren_throws() {
        CostCenter existing = existingCostCenter(5L, "CC05", 1, false, null);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.existsByParentAndActiveTrue(existing)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(5L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("active sub-centers");
    }

    @Test
    @DisplayName("deactivate() succeeds and also clears allowsMovement")
    void deactivate_withoutChildren_succeedsAndClearsAllowsMovement() {
        CostCenter existing = existingCostCenter(5L, "CC05", 1, true, null);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.existsByParentAndActiveTrue(existing)).thenReturn(false);

        service.deactivate(5L);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.isAllowsMovement()).isFalse();
    }

    @Test
    @DisplayName("activate() reactivates a cost center")
    void activate_succeeds() {
        CostCenter existing = existingCostCenter(5L, "CC05", 1, false, null);
        existing.setActive(false);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        service.activate(5L);

        assertThat(existing.isActive()).isTrue();
    }
}
