package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {

    // Retrieves all cost centers for a specific company ordered by code
    List<CostCenter> findByCompanyOrderByCodeAsc(Company company);

    // Finds only cost centers that allow direct accounting movements
    List<CostCenter> findByCompanyAndAllowsMovementTrue(Company company);

    // Gets root-level cost centers (those without a parent) for a company
    List<CostCenter> findByCompanyAndParentIsNull(Company company);

    // Retrieves direct children of a specific parent cost center
    List<CostCenter> findByParentId(Long parentId);

    boolean existsByParentId(Long parentId);

    boolean existsByParentAndActiveTrue(CostCenter costCenter);
    boolean existsByParent(CostCenter parent);
}
