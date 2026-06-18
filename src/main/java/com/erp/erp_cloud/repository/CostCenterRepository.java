package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    /**
     * ADAPTED: Retrieves all cost centers using the company primitive ID.
     */
    List<CostCenter> findByCompanyIdOrderByCodeAsc(Long companyId);

    /**
     * ADAPTED: Finds cost centers that allow direct accounting movements for a specific tenant ID.
     */
    List<CostCenter> findByCompanyIdAndAllowsMovementTrue(Long companyId);

    /**
     * ADAPTED: Gets root-level cost centers using the company primitive ID.
     */
    List<CostCenter> findByCompanyIdAndParentIsNull(Long companyId);

    /**
     * ADAPTED: Retrieves direct children of a specific parent cost center within a company context.
     */
    List<CostCenter> findByCompanyIdAndParentId(Long companyId, Long parentId);

    /**
     * ADAPTED: Checks if a cost center has any children using primitive IDs.
     */
    boolean existsByCompanyIdAndParentId(Long companyId, Long parentId);

    /**
     * ADAPTED: Checks if an active sub-center exists for a given parent within the tenant context.
     */
    boolean existsByCompanyIdAndParentIdAndActiveTrue(Long companyId, Long parentId);

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

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