package com.erp.erp_cloud.repository;


import com.erp.erp_cloud.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    /**
     * Checks if a role with the given code exists for a specific company.
     * Useful for validating both system-wide roles (companyId = null) and custom tenant roles.
     * * @param code The unique code representing the role (e.g., "ACCOUNTANT").
     * @param companyId The database ID of the company tenant.
     * @return true if the role exists, false otherwise.
     */
    boolean existsByCodeAndCompanyId(String code, Long companyId);

    /**
     * NEW (2026-10-29, Multiempresa Fase 3): roles selectable from the
     * user-management screen for a given company -- that company's own
     * roles plus system-wide ones (company IS NULL). Used by
     * GET /api/v1/roles and to validate every role id a caller tries to
     * assign/remove actually belongs where they claim.
     */
    @Query("SELECT r FROM Role r WHERE r.company.id = :companyId OR r.company IS NULL ORDER BY r.name")
    List<Role> findAvailableForCompany(@Param("companyId") Long companyId);
}
