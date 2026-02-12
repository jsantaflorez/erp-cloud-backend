package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CostCenterRequest;
import com.erp.erp_cloud.dto.CostCenterResponseDTO;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
@Service
@RequiredArgsConstructor
public class CostCenterService {

    private final CostCenterRepository repository;
    private final CompanyContext companyContext;

    /**
     * Retrieves all cost centers for the current company context.
     * Returns a list of DTOs to avoid circular reference issues.
     */
    @Transactional(readOnly = true)
    public List<CostCenterResponseDTO> listAll() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyOrderByCodeAsc(company)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Creates a new cost center after validating state consistency.
     */
    @Transactional
    public CostCenterResponseDTO create(CostCenterRequest request) {
        validateActiveStatus(request);

        CostCenter costCenter = new CostCenter();
        costCenter.setCompany(companyContext.getCurrentCompany());

        return mapAndSave(request, costCenter);
    }

    /**
     * Updates an existing cost center and ensures it remains consistent.
     */
    @Transactional
    public CostCenterResponseDTO update(Long id, CostCenterRequest request) {
        CostCenter costCenter = findEntityById(id);

        // Prevent setting allowsMovement to true if the center is inactive
        validateActiveStatus(request);

        return mapAndSave(request, costCenter);
    }

    /**
     * Soft-deletes a cost center by deactivating it.
     * Also disables movement to maintain financial integrity.
     */
    @Transactional
    public void deactivate(Long id) {
        CostCenter costCenter = findEntityById(id);

        // Hierarchy rule: Do not deactivate if active children exist
        boolean hasActiveChildren = repository.existsByParentAndActiveTrue(costCenter);
        if (hasActiveChildren) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot deactivate: This Cost Center has active sub-centers.");
        }

        // Logic consistency: Inactive centers cannot receive new transactions
        costCenter.setActive(false);
        costCenter.setAllowsMovement(false);

        repository.save(costCenter);
    }

    @Transactional
    public void activate(Long id) {
        CostCenter costCenter = findEntityById(id);
        costCenter.setActive(true);
        repository.save(costCenter);
    }


    /**
     * Gets root-level cost centers.
     * Used for the first level of the tree view.
     */
    @Transactional(readOnly = true)
    public List<CostCenterResponseDTO> getRoots() {
        return repository.findByCompanyAndParentIsNull(companyContext.getCurrentCompany())
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Retrieves only centers that allow accounting movements.
     * Crucial for the Journal Entry dropdowns.
     */
    @Transactional(readOnly = true)
    public List<CostCenterResponseDTO> getMovementAccounts() {
        return repository.findByCompanyAndAllowsMovementTrue(companyContext.getCurrentCompany())
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Retrieves children of a specific parent.
     * Used for expanding nodes in a tree view.
     */
    @Transactional(readOnly = true)
    public List<CostCenterResponseDTO> getChildren(Long parentId) {
        // We still use findByParentId but map the results to DTOs
        return repository.findByParentId(parentId)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    // --- Private Helper Methods ---

    /**
     * Ensures that 'allowsMovement' cannot be true if the record is 'inactive'.
     */
    private void validateActiveStatus(CostCenterRequest request) {
        if (request.isAllowsMovement() && !request.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Consistency error: Cannot allow movements on an inactive Cost Center.");
        }
    }

    /**
     * Maps the DTO to the Entity and handles level/parent logic.
     */
    private CostCenterResponseDTO mapAndSave(CostCenterRequest request, CostCenter costCenter) {
        costCenter.setCode(request.getCode());
        costCenter.setName(request.getName());
        costCenter.setAllowsMovement(request.isAllowsMovement());
        costCenter.setActive(request.isActive());

        if (request.getParentId() != null) {
            CostCenter parent = repository.findById(request.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent cost center not found"));

            costCenter.setParent(parent);
            costCenter.setLevel(parent.getLevel() + 1);
        } else {
            costCenter.setParent(null);
            costCenter.setLevel(1);
        }

        CostCenter saved = repository.save(costCenter);
        return mapToResponseDTO(saved);
    }

    private CostCenterResponseDTO mapToResponseDTO(CostCenter entity) {
        CostCenterResponseDTO dto = new CostCenterResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setLevel(entity.getLevel());
        dto.setActive(entity.isActive());
        dto.setAllowsMovement(entity.isAllowsMovement());
        dto.setParentId(entity.getParent() != null ? entity.getParent().getId() : null);
        return dto;
    }

    private CostCenter findEntityById(Long id) {
        Company company = companyContext.getCurrentCompany();
        return repository.findById(id)
                .filter(cc -> cc.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cost Center not found"));
    }
}