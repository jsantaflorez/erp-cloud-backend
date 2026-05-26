package com.erp.erp_cloud.repository;


import com.erp.erp_cloud.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}