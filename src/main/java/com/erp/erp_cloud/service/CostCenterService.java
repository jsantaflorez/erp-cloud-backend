package com.erp.erp_cloud.service;
import com.erp.erp_cloud.dto.CostCenterRequest;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.Company;

import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostCenterService {

    private final CostCenterRepository repository;
    private final CompanyContext companyContext;

    // List all cost centers for the current company
    @Transactional(readOnly = true)
    public List<CostCenter> listAll() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyOrderByCodeAsc(company);
    }

    // Create a new cost center using DTO
    @Transactional
    public CostCenter create(CostCenterRequest request) {
        CostCenter costCenter = new CostCenter();
        costCenter.setCode(request.getCode());
        costCenter.setName(request.getName());
        costCenter.setAllowsMovement(request.isAllowsMovement());
        costCenter.setActive(request.isActive());

        // Force assignment of current company from context
        costCenter.setCompany(companyContext.getCurrentCompany());

        // Hierarchy and level logic
        if (request.getParentId() != null) {
            CostCenter parent = repository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent cost center not found"));

            costCenter.setParent(parent);
            costCenter.setLevel(parent.getLevel() + 1);
        } else {
            costCenter.setParent(null);
            costCenter.setLevel(1);
        }

        return repository.save(costCenter);
    }
    // Get root cost centers for the current company
    @Transactional(readOnly = true)
    public List<CostCenter> getRoots() {
        return repository.findByCompanyAndParentIsNull(companyContext.getCurrentCompany());
    }

    // Get only centers that allow movement
    @Transactional(readOnly = true)
    public List<CostCenter> getMovementAccounts() {
        return repository.findByCompanyAndAllowsMovementTrue(companyContext.getCurrentCompany());
    }

    // Get children of a specific parent
    @Transactional(readOnly = true)
    public List<CostCenter> getChildren(Long parentId) {
        return repository.findByParentId(parentId);
    }
}