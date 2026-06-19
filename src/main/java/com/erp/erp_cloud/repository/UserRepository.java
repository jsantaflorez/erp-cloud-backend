package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * FIXED: Moved the companyId restriction to the JOIN ON clause to guarantee clean
     * collection filtering without breaking the LEFT JOIN behavior on the root User.
     */
    @Query("SELECT DISTINCT u FROM User u " +
            "LEFT JOIN FETCH u.userRoles ur ON ur.company.id = :companyId " +
            "LEFT JOIN FETCH ur.role r " +
            "LEFT JOIN FETCH r.permissions p " +
            "WHERE u.email = :email " +
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
}