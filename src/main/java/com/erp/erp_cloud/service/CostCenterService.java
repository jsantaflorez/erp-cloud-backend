package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CostCenterRequest;
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

    @Transactional(readOnly = true)
    public List<CostCenter> listAll() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyOrderByCodeAsc(company);
    }

    @Transactional
    public CostCenter create(CostCenterRequest request) {
        CostCenter costCenter = new CostCenter();
        costCenter.setCode(request.getCode());
        costCenter.setName(request.getName());
        costCenter.setAllowsMovement(request.isAllowsMovement());
        costCenter.setActive(request.isActive());
        costCenter.setCompany(companyContext.getCurrentCompany());

        if (request.getParentId() != null) {
            CostCenter parent = repository.findById(request.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent cost center not found"));

            costCenter.setParent(parent);
            costCenter.setLevel(parent.getLevel() + 1);
        } else {
            costCenter.setParent(null);
            costCenter.setLevel(1);
        }

        return repository.save(costCenter);
    }

    @Transactional
    public CostCenter update(Long id, CostCenterRequest request) {
        CostCenter costCenter = findById(id);

        costCenter.setCode(request.getCode());
        costCenter.setName(request.getName());
        costCenter.setAllowsMovement(request.isAllowsMovement());
        costCenter.setActive(request.isActive());

        if (request.getParentId() != null) {
            if (costCenter.getParent() == null || !costCenter.getParent().getId().equals(request.getParentId())) {
                CostCenter newParent = repository.findById(request.getParentId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "New parent not found"));

                costCenter.setParent(newParent);
                costCenter.setLevel(newParent.getLevel() + 1);
            }
        } else {
            costCenter.setParent(null);
            costCenter.setLevel(1);
        }

        return repository.save(costCenter);
    }

    @Transactional
    public void delete(Long id) {
        // 1. Find the entity using the service's internal findById (validates ownership)
        CostCenter entity = this.findById(id);

        // 2. BLOCK: Prevent deletion if the cost center has sub-centers [cite: 2026-01-14]
        boolean hasChildren = repository.existsByParent(entity);
        if (hasChildren) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete Cost Center because it has sub-centers.");
        }

        // 3. TODO: Add check for accounting movements before final deletion [cite: 2026-01-17]

        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<CostCenter> getRoots() {
        return repository.findByCompanyAndParentIsNull(companyContext.getCurrentCompany());
    }

    @Transactional(readOnly = true)
    public List<CostCenter> getMovementAccounts() {
        return repository.findByCompanyAndAllowsMovementTrue(companyContext.getCurrentCompany());
    }

    @Transactional(readOnly = true)
    public List<CostCenter> getChildren(Long parentId) {
        return repository.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public CostCenter findById(Long id) {
        Company company = companyContext.getCurrentCompany();
        return repository.findById(id)
                .filter(acc -> acc.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cost Center not found"));
    }
}