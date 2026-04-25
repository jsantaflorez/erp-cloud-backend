package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CostCenterRequest;
import com.erp.erp_cloud.dto.CostCenterResponseDTO;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CostCenterService {

    private static final Logger log = LoggerFactory.getLogger(CostCenterService.class);

    private final CostCenterRepository repository;
    private final CompanyContext companyContext;

    /**
     * Retrieves all cost centers for the current company context.
     * Returns a list of DTOs to avoid circular reference issues.
     */
    @Transactional(readOnly = true)
    public List<CostCenterResponseDTO> listAll() {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing all cost centers for company: {}", company.getId());

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
        Company company = companyContext.getCurrentCompany();

        log.debug("Creating cost center with code: {} for company: {}", request.getCode(), company.getId());

        validateActiveStatus(request);

        CostCenter costCenter = new CostCenter();
        costCenter.setCompany(company);

        CostCenterResponseDTO response = mapAndSave(request, costCenter);

        log.info("Cost center created successfully with id: {}", response.getId());

        return response;
    }

    /**
     * Updates an existing cost center and ensures it remains consistent.
     */
    @Transactional
    public CostCenterResponseDTO update(Long id, CostCenterRequest request) {
        log.debug("Updating cost center id: {}", id);

        CostCenter costCenter = findEntityById(id);

        // Prevent setting allowsMovement to true if the center is inactive
        validateActiveStatus(request);



        CostCenterResponseDTO response = mapAndSave(request, costCenter);

        log.info("Cost center {} updated successfully", id);

        return response;
    }

    /**
     * Soft-deletes a cost center by deactivating it.
     * Also disables movement to maintain financial integrity.
     */
    @Transactional
    public void deactivate(Long id) {
        log.debug("Deactivating cost center id: {}", id);

        CostCenter costCenter = findEntityById(id);

        // Hierarchy rule: Do not deactivate if active children exist
        boolean hasActiveChildren = repository.existsByParentAndActiveTrue(costCenter);
        if (hasActiveChildren) {
            throw new InvalidOperationException(
                    "Cannot deactivate: This Cost Center has active sub-centers."
            );
        }

        // Logic consistency: Inactive centers cannot receive new transactions
        costCenter.setActive(false);
        costCenter.setAllowsMovement(false);

        repository.save(costCenter);

        log.info("Cost center {} deactivated successfully", id);
    }

    @Transactional
    public void activate(Long id) {
        log.debug("Activating cost center id: {}", id);

        CostCenter costCenter = findEntityById(id);
        costCenter.setActive(true);
        repository.save(costCenter);

        log.info("Cost center {} activated successfully", id);
    }

    /**
     * Gets root-level cost centers.
     * Used for the first level of the tree view.
     */
    @Transactional(readOnly = true)
    public List<CostCenterResponseDTO> getRoots() {
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing root cost centers for company: {}", company.getId());

        return repository.findByCompanyAndParentIsNull(company)
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
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing cost centers that allow movements for company: {}", company.getId());

        return repository.findByCompanyAndAllowsMovementTrue(company)
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
        log.debug("Listing children cost centers for parent id: {}", parentId);

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
            throw new InvalidOperationException(
                    "Consistency error: Cannot allow movements on an inactive Cost Center."
            );
        }
    }

    /**
     * Maps the DTO to the Entity and handles level/parent logic.
     */
     private CostCenterResponseDTO mapAndSave(CostCenterRequest request, CostCenter costCenter) {

        // --- VALIDATION 1: Parent Hierarchy Rule ---
        // A Cost Center marked as 'allowsMovement' (operational) cannot have sub-centers.
        if (request.getParentId() != null) {
            CostCenter parent = repository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter (parent)", request.getParentId()));

            if (parent.isAllowsMovement()) {
                log.warn("Hierarchy violation: Parent center {} is an operational node", parent.getId());
                throw new InvalidOperationException(
                        "Cannot add sub-center: The parent center is marked to allow movements."
                );
            }
            costCenter.setParent(parent);
            costCenter.setLevel(parent.getLevel() + 1);
        } else {
            costCenter.setParent(null);
            costCenter.setLevel(1);
        }

        // --- VALIDATION 2: Downward Consistency Rule ---
        // If an existing center is being updated to 'allowsMovement', it must not have children.
         if (request.isAllowsMovement() && costCenter.getId() != null) {
             // We use the primary key (ID) to verify if this center already has children
             if (repository.existsByParentId(costCenter.getId())) {
                 log.warn("Consistency violation: Center ID {} has children", costCenter.getId());
                 throw new InvalidOperationException(
                         "This center has sub-centers and cannot be marked to allow movements."
                 );
             }
         }

        // Map remaining fields from request to entity
        costCenter.setCode(request.getCode());
        costCenter.setName(request.getName());
        costCenter.setAllowsMovement(request.isAllowsMovement());
        costCenter.setActive(request.isActive());

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

        log.debug("Finding cost center by id: {} for company: {}", id, company.getId());

        return repository.findById(id)
                .filter(cc -> cc.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("CostCenter", id));
    }
}