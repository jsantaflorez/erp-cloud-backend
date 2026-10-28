package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Phase 1 (Authentication): Finds basic identity records globally by email.
     * Used for the initial credentials verification (password check, MFA, lockouts).
     */
    Optional<User> findByEmail(String email);
    /**
     * Phase 2 (Tenant Authorization): Single database round-trip that fetches
     * User + UserRoles + Roles + Permissions strictly restricted to the active Company ID.
     * FIXED: Aligned with Hibernate standard specifications by isolating tenant filtering
     * inside the WHERE clause to avoid SemanticException, while maintaining Eager Fetching.
     */
    @Query("SELECT DISTINCT u FROM User u " +
            "LEFT JOIN FETCH u.userRoles ur " +
            "LEFT JOIN FETCH ur.role r " +
            "LEFT JOIN FETCH r.permissions p " +
            "WHERE u.email = :email " +
            "AND (ur IS NULL OR ur.company.id = :companyId) " +
            "AND u.active = true")
    Optional<User> findByEmailWithRolesAndPermissionsForCompany(
            @Param("email") String email,
            @Param("companyId") Long companyId);


    /**
     * ADAPTED LIGHTWEIGHT OPTION: Retrieves only permission codes for the current tenant.
     * Optimized using primitive Long companyId to avoid heavy entity graphs.
     */
    @Query("SELECT p.code FROM UserRole ur " +
            "JOIN ur.role r " +
            "JOIN r.permissions p " +
            "WHERE ur.user.email = :email " +
            "AND ur.company.id = :companyId")
    Set<String> findPermissionCodesByEmailAndCompanyId(
            @Param("email") String email,
            @Param("companyId") Long companyId);

    /**
     * NEW (2026-10-26, Multiempresa Fase 1): companies the user can
     * actively switch into -- every company where the user holds at
     * least one UserRole, restricted to active companies only (an
     * inactive/suspended tenant should not appear as a switch target
     * even if a stale UserRole row still references it). Used by
     * GET /api/v1/session/companies to populate the AppHeader dropdown.
     */
    @Query("SELECT DISTINCT ur.company FROM UserRole ur " +
            "WHERE ur.user.email = :email " +
            "AND ur.company.active = true " +
            "ORDER BY ur.company.legalName")
    List<Company> findAccessibleCompaniesByEmail(@Param("email") String email);
}
